package egovframework.eeum.base.crypto;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 필드 암호화 키 공급자 (AES-256, crypto-standard.md §6).
 *
 * <p>키는 env 주입(`${FIELD_ENC_KEY_V1}` — crypto-standard §6 키명과 일치). 현재 키버전 = v1.
 * base64 디코딩 + 32바이트 길이 검증을 수행한다. 키버전 맵 구조로 향후 rotation(encrypt 1개 + 구버전 decryptOnly)을 지원한다.
 *
 * <p><b>Phase A 정책(중요):</b> <b>키 미설정이어도 부팅을 깨지 않는다.</b> 아직 암호화 필드가 실제 wiring되지 않았으므로
 * dev/prod에 키가 없을 수 있다. 따라서 <b>부팅 fail-fast는 자동 적용하지 않고</b>, 키는 <b>사용 시점</b>에만 요구한다
 * (미설정 시 {@link FieldCryptoException}). 단, <b>설정된 키가 형식 오류면</b>(present-but-invalid) 생성 시 예외 —
 * 이는 의도적 오설정이다. 부팅 단계 강제 검증(fail-fast)은 {@link #requireConfigured()}로 분리해 두며,
 * Phase B/C에서 실제 wiring 활성화 시 {@code @PostConstruct} 등으로 호출하도록 한다.
 *
 * <p>키값은 어떤 경로로도 로그/예외 메시지에 노출하지 않는다.
 */
@Component
public class FieldEncryptionKeyProvider {

	/** AES-256 키 길이(바이트). */
	private static final int KEY_BYTES = 32;

	/** 신규 암호화에 사용하는 현재 키버전. */
	private static final int CURRENT_VERSION = 1;

	private final Map<Integer, SecretKey> keysByVersion = new HashMap<>();

	public FieldEncryptionKeyProvider(@Value("${FIELD_ENC_KEY_V1:}") String keyV1Base64) {
		// 키 미설정(빈 값)이면 비워둠 — 부팅을 깨지 않는다(Phase A). 설정됐는데 형식 오류면 예외(의도적 오설정).
		if (keyV1Base64 != null && !keyV1Base64.isBlank()) {
			keysByVersion.put(1, parseAndValidate(keyV1Base64));
		}
	}

	/** 현재 키버전(v1)이 설정되어 있는지. */
	public boolean isConfigured() {
		return keysByVersion.containsKey(CURRENT_VERSION);
	}

	/** 신규 암호화용 현재 키버전. */
	public int currentVersion() {
		return CURRENT_VERSION;
	}

	/** 신규 암호화용 키(현재 버전). 미설정 시 예외. */
	public SecretKey encryptionKey() {
		return requireKey(CURRENT_VERSION);
	}

	/** 복호화용 키(지정 버전 — rotation 시 구버전 decryptOnly). 미설정 시 예외. */
	public SecretKey keyForVersion(int version) {
		return requireKey(version);
	}

	/**
	 * 부팅 단계 강제 검증용(fail-fast) — Phase A에서는 <b>자동 호출하지 않는다</b>.
	 * Phase B/C에서 암호화 필드 wiring 활성화 시점에 호출해 키 미설정을 부팅 실패로 승격한다.
	 */
	public void requireConfigured() {
		if (!isConfigured()) {
			throw new FieldCryptoException("field encryption key (current version) is not configured");
		}
	}

	private SecretKey requireKey(int version) {
		SecretKey key = keysByVersion.get(version);
		if (key == null) {
			throw new FieldCryptoException("field encryption key is not configured for the requested version");
		}
		return key;
	}

	private static SecretKey parseAndValidate(String base64) {
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64.trim());
		} catch (IllegalArgumentException e) {
			throw new FieldCryptoException("invalid field encryption key (not valid base64)");
		}
		if (raw.length != KEY_BYTES) {
			// 길이만 보고(키값 미노출)
			throw new FieldCryptoException("invalid field encryption key (expected base64 of 32 bytes)");
		}
		return new SecretKeySpec(raw, "AES");
	}
}
