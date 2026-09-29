package egovframework.eeum.base.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;

import org.springframework.stereotype.Component;

/**
 * 검색·중복판단용 필드 해시 (HMAC-SHA256, crypto-standard.md §3).
 *
 * <ul>
 *   <li>방식: HMAC-SHA256(전용 키 — 암호화 키와 분리, {@link FieldHmacKeyProvider}), 출력 = <b>hex 소문자 64자</b>.</li>
 *   <li>입력은 <b>정규화된 값</b>이어야 한다(전화번호는 {@link PhoneNumbers#normalizeMobile}로 정규화 후 전달).</li>
 *   <li>키 미설정 시 {@link FieldCryptoException}(fail-closed) — 해시 없이 우회 저장 금지.</li>
 *   <li>예외/로그에 키·평문 원문을 절대 담지 않는다.</li>
 * </ul>
 */
@Component
public class FieldHasher {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private final FieldHmacKeyProvider keyProvider;

	public FieldHasher(FieldHmacKeyProvider keyProvider) {
		this.keyProvider = keyProvider;
	}

	/**
	 * 정규화된 값의 HMAC-SHA256 해시(hex 소문자 64자)를 반환한다.
	 *
	 * @param normalizedValue 정규화 완료된 평문(공백/일관성은 호출측 책임) — null/빈문자열 불가
	 * @return hex 소문자 64자
	 * @throws FieldCryptoException 키 미설정(fail-closed) 또는 계산 실패
	 */
	public String hmacHex(String normalizedValue) {
		if (normalizedValue == null || normalizedValue.isEmpty()) {
			throw new FieldCryptoException("hash input must not be null/empty (normalize before hashing)");
		}
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(keyProvider.hmacKey());
			byte[] digest = mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8));
			return toHexLower(digest);
		} catch (FieldCryptoException e) {
			throw e;
		} catch (GeneralSecurityException e) {
			throw new FieldCryptoException("field hashing failed"); // 원문/키 미포함
		}
	}

	private static String toHexLower(byte[] bytes) {
		char[] out = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int v = bytes[i] & 0xFF;
			out[i * 2] = HEX[v >>> 4];
			out[i * 2 + 1] = HEX[v & 0x0F];
		}
		return new String(out);
	}
}
