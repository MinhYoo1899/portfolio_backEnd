package egovframework.eeum.file.service.file.storage.impl;

import java.util.Properties;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.storage.SftpProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * SFTP 세션 풀 — 핸드셰이크 비용이 큰 SSH 세션을 재사용한다(요청마다 새 연결 금지, codex 권고).
 *
 * <ul>
 *   <li>동시 사용 세션 수를 {@code poolMax} 로 제한(Semaphore) — 고갈 시 borrow 타임아웃 후 503.</li>
 *   <li>borrow 한 세션은 단독 사용, 반납 시 유효하면 재사용 큐로, 아니면 폐기.</li>
 *   <li><b>호스트키 검증 필수</b>: {@code known_hosts} 로드 + {@code StrictHostKeyChecking=yes}
 *       (미설정이면 {@link SftpProperties#isConfigured()} 가 false → 파일기능 비활성, MITM 방지).</li>
 *   <li>지연 초기화 — host 미설정이면 부팅을 막지 않는다(소셜 client-id fail-safe 와 동일 철학).</li>
 * </ul>
 */
@Slf4j
@Component
public class SftpSessionPool {

	private final SftpProperties props;

	private final LinkedBlockingDeque<Session> idle = new LinkedBlockingDeque<>();
	private Semaphore permits;
	private JSch jsch;
	private volatile boolean initialized;
	private volatile boolean closed;

	public SftpSessionPool(SftpProperties props) {
		this.props = props;
	}

	private synchronized void ensureInit() {
		if (initialized) {
			return;
		}
		try {
			JSch instance = new JSch();
			instance.setKnownHosts(props.getKnownHostsPath()); // 호스트키 검증 기준
			this.jsch = instance;
			this.permits = new Semaphore(props.getPoolMax(), true);
			this.initialized = true;
		} catch (JSchException e) {
			log.error("[FILE-SFTP] known_hosts 로드 실패: {}", props.getKnownHostsPath());
			throw new BusinessException(FileErrorCode.FILE_STORAGE_UNAVAILABLE);
		}
	}

	/** 세션 대여 — 미설정/풀고갈/연결실패는 모두 호출 측에서 503/502 로 변환된다. */
	public Session borrow() {
		if (!props.isConfigured()) {
			throw new BusinessException(FileErrorCode.FILE_STORAGE_UNAVAILABLE);
		}
		ensureInit();
		boolean acquired;
		try {
			acquired = permits.tryAcquire(props.getPoolBorrowTimeoutMs(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BusinessException(FileErrorCode.FILE_STORAGE_UNAVAILABLE);
		}
		if (!acquired) {
			log.warn("[FILE-SFTP] 세션 풀 고갈 — borrow 타임아웃({}ms)", props.getPoolBorrowTimeoutMs());
			throw new BusinessException(FileErrorCode.FILE_STORAGE_UNAVAILABLE);
		}
		try {
			Session reuse = idle.pollFirst();
			while (reuse != null) {
				if (reuse.isConnected()) {
					return reuse;
				}
				safeDisconnect(reuse); // stale
				reuse = idle.pollFirst();
			}
			return createSession();
		} catch (RuntimeException e) {
			permits.release();
			throw e;
		}
	}

	/** 세션 반납 — broken 이거나 끊겼으면 폐기, 아니면 재사용 큐로. permit 은 항상 반환. */
	public void release(Session session, boolean broken) {
		try {
			if (session == null) {
				return;
			}
			if (broken || closed || !session.isConnected()) {
				safeDisconnect(session); // shutdown 이후 반납분은 재큐잉 대신 폐기(codex 코드검토)
			} else {
				idle.offerFirst(session);
			}
		} finally {
			if (permits != null) {
				permits.release();
			}
		}
	}

	private Session createSession() {
		try {
			Session session = jsch.getSession(props.getUsername(), props.getHost(), props.getPort());
			session.setPassword(props.getPassword());
			Properties config = new Properties();
			config.put("StrictHostKeyChecking", "yes"); // known_hosts 강제(=no 금지)
			session.setConfig(config);
			session.connect(props.getConnectTimeoutMs());
			return session;
		} catch (JSchException e) {
			log.warn("[FILE-SFTP] 세션 연결 실패: {}", e.getClass().getSimpleName());
			throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR);
		}
	}

	private static void safeDisconnect(Session session) {
		try {
			session.disconnect();
		} catch (RuntimeException ignore) {
			// best-effort
		}
	}

	@PreDestroy
	void shutdown() {
		closed = true;
		Session s;
		while ((s = idle.pollFirst()) != null) {
			safeDisconnect(s);
		}
	}
}
