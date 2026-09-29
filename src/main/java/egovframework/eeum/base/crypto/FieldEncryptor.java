package egovframework.eeum.base.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

import org.springframework.stereotype.Component;

/**
 * 필드 단위 인증암호화 (AES-256-GCM, crypto-standard.md §2).
 *
 * <ul>
 *   <li>알고리즘: AES/GCM/NoPadding, IV 96bit 랜덤(매 암호화마다 신규), 인증태그 128bit.</li>
 *   <li>AAD = 필드ID(UTF-8) — 한 컬럼 암호문을 다른 컬럼으로 이식하면 복호화 실패하도록 바인딩(필드ID 동결 계약).</li>
 *   <li>포맷: {@code v{키버전}:Base64(IV ‖ CIPHERTEXT ‖ TAG)} (GCM doFinal 결과가 CIPHERTEXT‖TAG).</li>
 *   <li>null/빈문자열 = "없음" → 그대로 통과(암호화하지 않음).</li>
 *   <li>예외({@link FieldCryptoException})에는 키·평문·암호문 원문을 절대 담지 않는다. 별도 로깅도 하지 않는다.</li>
 * </ul>
 *
 * <p>Phase A 범위: 모듈/단위검증만. TypeHandler·서비스 wiring은 Phase B/C.
 */
@Component
public class FieldEncryptor {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;       // 96bit
	private static final int TAG_BITS = 128;
	private static final int TAG_BYTES = 16;      // 128bit 인증태그(바이트)

	private final FieldEncryptionKeyProvider keyProvider;
	private final SecureRandom secureRandom = new SecureRandom();

	public FieldEncryptor(FieldEncryptionKeyProvider keyProvider) {
		this.keyProvider = keyProvider;
	}

	/**
	 * 평문 암호화. {@code null → null}, {@code "" → ""}(없음, 통과). 그 외는 {@code v{ver}:Base64(IV‖CT‖TAG)}.
	 *
	 * @param plainText 평문(null/빈문자열 허용)
	 * @param fieldId   AAD로 바인딩할 필드ID(예: {@code "PRTNR_MST.PIC_MBL_TELNO"}) — 공백 불가
	 */
	public String encrypt(String plainText, String fieldId) {
		if (plainText == null || plainText.isEmpty()) {
			return plainText;
		}
		requireFieldId(fieldId);
		try {
			int version = keyProvider.currentVersion();
			byte[] iv = new byte[IV_BYTES];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, keyProvider.encryptionKey(), new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(fieldId.getBytes(StandardCharsets.UTF_8));
			byte[] cipherAndTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			byte[] out = ByteBuffer.allocate(iv.length + cipherAndTag.length).put(iv).put(cipherAndTag).array();
			return "v" + version + ":" + Base64.getEncoder().encodeToString(out);
		} catch (FieldCryptoException e) {
			throw e;
		} catch (GeneralSecurityException e) {
			throw new FieldCryptoException("field encryption failed"); // 원문/키 미포함
		}
	}

	/**
	 * 암호문 복호화. {@code null → null}, {@code "" → ""}. 포맷/키/AAD/태그 불일치는 {@link FieldCryptoException}.
	 *
	 * @param cipherText {@code v{ver}:Base64(...)} 또는 null/빈문자열
	 * @param fieldId    암호화 시 사용한 동일 필드ID(AAD)
	 */
	public String decrypt(String cipherText, String fieldId) {
		if (cipherText == null || cipherText.isEmpty()) {
			return cipherText;
		}
		requireFieldId(fieldId);
		int version = parseVersion(cipherText);
		byte[] data = decodeBody(cipherText);
		if (data.length < IV_BYTES + TAG_BYTES) {
			// IV(12) + 최소 인증태그(16)도 못 담는 길이 → 포맷 오류 (codex 권고: 길이검증 정밀화)
			throw new FieldCryptoException("invalid ciphertext format");
		}
		byte[] iv = Arrays.copyOfRange(data, 0, IV_BYTES);
		byte[] cipherAndTag = Arrays.copyOfRange(data, IV_BYTES, data.length);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, keyProvider.keyForVersion(version), new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(fieldId.getBytes(StandardCharsets.UTF_8));
			return new String(cipher.doFinal(cipherAndTag), StandardCharsets.UTF_8);
		} catch (FieldCryptoException e) {
			throw e;
		} catch (AEADBadTagException e) {
			// 키 불일치 / AAD 불일치 / 변조 — 평문 노출 없이
			throw new FieldCryptoException("decryption failed (authentication tag mismatch)");
		} catch (GeneralSecurityException e) {
			throw new FieldCryptoException("decryption failed");
		}
	}

	/** 암호문 여부 — {@code ^v\d+:} 패턴(crypto-standard §4). null/빈문자열은 false. */
	public boolean isCipherText(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		try {
			parseVersion(value);
			return true;
		} catch (FieldCryptoException e) {
			return false;
		}
	}

	private static int parseVersion(String cipherText) {
		int colon = cipherText.indexOf(':');
		if (cipherText.charAt(0) != 'v' || colon < 2) {
			throw new FieldCryptoException("invalid ciphertext format");
		}
		try {
			int version = Integer.parseInt(cipherText.substring(1, colon));
			if (version <= 0) {
				throw new FieldCryptoException("invalid ciphertext format");
			}
			return version;
		} catch (NumberFormatException e) {
			throw new FieldCryptoException("invalid ciphertext format");
		}
	}

	private static byte[] decodeBody(String cipherText) {
		String body = cipherText.substring(cipherText.indexOf(':') + 1);
		try {
			return Base64.getDecoder().decode(body);
		} catch (IllegalArgumentException e) {
			throw new FieldCryptoException("invalid ciphertext format");
		}
	}

	private static void requireFieldId(String fieldId) {
		if (fieldId == null || fieldId.isBlank()) {
			throw new FieldCryptoException("fieldId (AAD) must not be blank");
		}
	}
}
