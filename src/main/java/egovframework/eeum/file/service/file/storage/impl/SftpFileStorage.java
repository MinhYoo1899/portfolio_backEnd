package egovframework.eeum.file.service.file.storage.impl;

import java.io.InputStream;

import org.springframework.stereotype.Component;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.storage.FileStorage;
import egovframework.eeum.file.service.file.storage.SftpProperties;
import egovframework.eeum.file.service.file.storage.StoredResource;
import lombok.extern.slf4j.Slf4j;

/**
 * SFTP 기반 파일 저장소.
 *
 * <ul>
 *   <li>저장: 임시키(.tmp)로 업로드 후 <b>원자적 rename</b> → 부분파일이 최종경로로 노출되지 않음(codex 권고).</li>
 *   <li>다운로드: 채널/세션을 {@link StoredResource} 에 묶어 반환 — 스트림 소비 후 호출 측이 close 시 반납.</li>
 *   <li>경로: {@code base + / + relativeKey}. relativeKey 는 호출 측이 UUID·검증확장자로만 구성(경로조작 차단).</li>
 * </ul>
 */
@Slf4j
@Component
public class SftpFileStorage implements FileStorage {

	private static final String STORAGE_TYPE = "SFTP";
	private static final String SFTP_CHANNEL = "sftp";

	private final SftpProperties props;
	private final SftpSessionPool pool;

	public SftpFileStorage(SftpProperties props, SftpSessionPool pool) {
		this.props = props;
		this.pool = pool;
	}

	@Override
	public String storageTypeCode() {
		return STORAGE_TYPE;
	}

	@Override
	public boolean isAvailable() {
		return props.isConfigured();
	}

	@Override
	public void store(String relativeKey, InputStream content) {
		validateKey(relativeKey);
		Session session = pool.borrow();
		boolean broken = false;
		ChannelSftp channel = null;
		String full = fullPath(relativeKey);
		String tmp = full + ".tmp";
		try {
			channel = openChannel(session);
			mkdirs(channel, parentOf(full));
			channel.put(content, tmp); // 임시 업로드
			channel.rename(tmp, full); // 원자적 확정(대상 존재 시 실패 — 기존 파일 보존, codex 코드검토)
		} catch (SftpException e) {
			broken = isFatal(e);
			cleanupTmp(channel, tmp);
			log.warn("[FILE-SFTP] store 실패 key={}, id={}", relativeKey, e.id);
			throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR);
		} catch (JSchException e) {
			broken = true;
			throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR);
		} finally {
			disconnect(channel);
			pool.release(session, broken);
		}
	}

	@Override
	public StoredResource open(String relativeKey) {
		validateKey(relativeKey);
		Session session = pool.borrow();
		ChannelSftp channel = null;
		try {
			channel = openChannel(session);
			InputStream in = channel.get(fullPath(relativeKey));
			final ChannelSftp ch = channel;
			// 스트림 소비 완료 후 close 시 채널 닫고 세션 반납
			return new StoredResource(in, -1L, () -> {
				disconnect(ch);
				pool.release(session, false);
			});
		} catch (SftpException e) {
			disconnect(channel);
			pool.release(session, isFatal(e));
			if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
				throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
			}
			log.warn("[FILE-SFTP] open 실패 key={}, id={}", relativeKey, e.id);
			throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR);
		} catch (JSchException e) {
			disconnect(channel);
			pool.release(session, true);
			throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR);
		}
	}

	@Override
	public void delete(String relativeKey) {
		validateKey(relativeKey);
		Session session = pool.borrow();
		boolean broken = false;
		ChannelSftp channel = null;
		try {
			channel = openChannel(session);
			channel.rm(fullPath(relativeKey));
		} catch (SftpException e) {
			broken = isFatal(e);
			if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
				log.warn("[FILE-SFTP] delete 실패(무시) key={}, id={}", relativeKey, e.id);
			}
			// 멱등 — 없거나 실패해도 예외 던지지 않음(orphan 정리는 별도)
		} catch (JSchException e) {
			broken = true;
			log.warn("[FILE-SFTP] delete 채널 실패(무시) key={}", relativeKey);
		} finally {
			disconnect(channel);
			pool.release(session, broken);
		}
	}

	// ------------------------------------------------------------------

	private ChannelSftp openChannel(Session session) throws JSchException {
		ChannelSftp channel = (ChannelSftp) session.openChannel(SFTP_CHANNEL);
		channel.connect(props.getConnectTimeoutMs());
		return channel;
	}

	private String fullPath(String relativeKey) {
		String base = props.getBasePath();
		String k = relativeKey.startsWith("/") ? relativeKey.substring(1) : relativeKey;
		if (base == null || base.isBlank()) {
			return k;
		}
		String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		return b + "/" + k;
	}

	private static String parentOf(String path) {
		int idx = path.lastIndexOf('/');
		return idx <= 0 ? "" : path.substring(0, idx);
	}

	/** 저장키 방어 — 상대키만 허용(.. / 절대경로 / 역슬래시 거부, 경로조작 차단 — codex 코드검토). */
	private static void validateKey(String key) {
		if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..") || key.contains("\\")) {
			throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR);
		}
	}

	/** mkdir -p — 세그먼트별로 stat 후 없으면 mkdir. */
	private static void mkdirs(ChannelSftp channel, String dir) throws SftpException {
		if (dir == null || dir.isBlank()) {
			return;
		}
		boolean absolute = dir.startsWith("/");
		String path = absolute ? "" : "";
		String[] segs = dir.split("/");
		StringBuilder cur = new StringBuilder(absolute ? "" : "");
		for (String seg : segs) {
			if (seg.isEmpty()) {
				continue;
			}
			cur.append('/').append(seg);
			String p = absolute ? cur.toString() : cur.substring(1);
			try {
				channel.stat(p);
			} catch (SftpException e) {
				channel.mkdir(p);
			}
		}
	}

	private void cleanupTmp(ChannelSftp channel, String tmp) {
		if (channel == null) {
			return;
		}
		try {
			channel.rm(tmp);
		} catch (SftpException ignore) {
			// best-effort
		}
	}

	/** 연결 자체가 깨진 류면 세션 폐기 대상. */
	private static boolean isFatal(SftpException e) {
		return e.id == ChannelSftp.SSH_FX_CONNECTION_LOST || e.id == ChannelSftp.SSH_FX_FAILURE;
	}

	private static void disconnect(ChannelSftp channel) {
		if (channel != null) {
			try {
				channel.disconnect();
			} catch (RuntimeException ignore) {
				// best-effort
			}
		}
	}
}
