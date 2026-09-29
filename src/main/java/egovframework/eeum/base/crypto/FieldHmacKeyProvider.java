package egovframework.eeum.base.crypto;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 검색·중복판단용 해시(HMAC-SHA256) 키 공급자 (crypto-standard.md §3·§6).
 *
 * <p>키는 env 주입(`${FIELD_HMAC_KEY_V1}` — crypto-standard §6 키명과 일치). 현재 키버전 = v1.
 * base64 디코딩 + 32바이트 길이 검증을 수행한다. <b>암호화 키(FIELD_ENC_KEY)와 반드시 분리</b>된 별도 시크릿이다
 * (crypto-standard §3 — 키 재사용 금지). 회전 시 버전 맵 구조로 확장한다.
 *
 * <p><b>fail-closed 정책:</b> {@link FieldEncryptionKeyProvider}와 동일하게 <b>키 미설정이어도 부팅을 깨지 않는다</b>
 * (전화번호 저장 경로가 아직 없을 수 있음). 대신 <b>사용 시점</b>(해시 계산)에 미설정이면 {@link FieldCryptoException}으로
 * 실패시킨다(fail-closed — 해시 없이 평문 저장 우회 금지). <b>설정됐는데 형식 오류면</b> 생성 시 예외(의도적 오설정).
 *
 * <p>키값은 어떤 경로로도 로그/예외 메시지에 노출하지 않는다.
 */
@Component
public class FieldHmacKeyProvider {

	/** HMAC-SHA256 키 길이(바이트). */
	private static final int KEY_BYTES = 32;

	/** 신규 해시에 사용하는 현재 키버전. */
	private static final int CURRENT_VERSION = 1;

	/** HMAC 키 알고리즘(SecretKeySpec). */
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final Map<Integer, SecretKey> keysByVersion = new HashMap<>();

	public FieldHmacKeyProvider(@Value("${FIELD_HMAC_KEY_V1:}") String keyV1Base64) {
		// 키 미설정(빈 값)이면 비워둠 — 부팅을 깨지 않는다. 설정됐는데 형식 오류면 예외(의도적 오설정).
		if (keyV1Base64 != null && !keyV1Base64.isBlank()) {
			keysByVersion.put(1, parseAndValidate(keyV1Base64));
		}
	}

	/** 현재 키버전(v1)이 설정되어 있는지. */
	public boolean isConfigured() {
		return keysByVersion.containsKey(CURRENT_VERSION);
	}

	/** 신규 해시용 현재 키버전. */
	public int currentVersion() {
		return CURRENT_VERSION;
	}

	/** 신규 해시용 키(현재 버전). 미설정 시 예외(fail-closed). */
	public SecretKey hmacKey() {
		return requireKey(CURRENT_VERSION);
	}

	private SecretKey requireKey(int version) {
		SecretKey key = keysByVersion.get(version);
		if (key == null) {
			throw new FieldCryptoException("field HMAC key is not configured for the requested version");
		}
		return key;
	}

	private static SecretKey parseAndValidate(String base64) {
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64.trim());
		} catch (IllegalArgumentException e) {
			throw new FieldCryptoException("invalid field HMAC key (not valid base64)");
		}
		if (raw.length != KEY_BYTES) {
			// 길이만 보고(키값 미노출)
			throw new FieldCryptoException("invalid field HMAC key (expected base64 of 32 bytes)");
		}
		return new SecretKeySpec(raw, HMAC_ALGORITHM);
	}
}
