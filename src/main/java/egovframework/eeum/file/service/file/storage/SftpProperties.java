package egovframework.eeum.file.service.file.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * SFTP 저장소 접속 설정 — {@code eeum.file.sftp.*} (env 주입).
 *
 * <p>{@code host} 가 비어 있으면 파일 저장 기능은 비활성(fail-safe — 부팅은 정상).
 * {@code knownHostsPath} 가 비어 있으면(호스트키 미검증) 파일 기능을 켜지 않는다(StrictHostKeyChecking=no 금지).
 */
@Component
@ConfigurationProperties(prefix = "eeum.file.sftp")
@Getter
@Setter
public class SftpProperties {

	/** SFTP 호스트(빈값=파일기능 비활성). */
	private String host;

	/** SFTP 포트(SSH, 기본 22). */
	private int port = 22;

	/** 계정. */
	private String username;

	/** 비밀번호(또는 추후 키 인증으로 대체). */
	private String password;

	/** 저장 베이스 경로(chroot 계정이면 chroot 내부 경로). */
	private String basePath = "/storage/eeum";

	/** 호스트키 검증용 known_hosts 파일 경로(필수 — 비면 파일기능 비활성). */
	private String knownHostsPath;

	/** 연결 타임아웃(ms). */
	private int connectTimeoutMs = 5000;

	/** 세션 풀 최대 동시 개수. */
	private int poolMax = 4;

	/** 풀 borrow 대기 타임아웃(ms) — 초과 시 503. */
	private long poolBorrowTimeoutMs = 5000;

	/** 저장 기능 활성 여부 — host + knownHostsPath 둘 다 설정돼야 활성(보안). */
	public boolean isConfigured() {
		return hasText(host) && hasText(username) && hasText(knownHostsPath);
	}

	private static boolean hasText(String v) {
		return v != null && !v.isBlank();
	}
}
