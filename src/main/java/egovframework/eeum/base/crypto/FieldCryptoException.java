package egovframework.eeum.base.crypto;

/**
 * 필드 암호화/복호화 실패 예외 (crypto-standard.md).
 *
 * <p><b>보안 계약:</b> 메시지/원인에 <b>키·평문·암호문 원문을 절대 포함하지 않는다</b>(로그/응답 노출 방지).
 * 안전한 분류 메시지만 담는다. 복호화 실패(키 불일치/AAD 불일치/변조)는 사용자에게 평문 노출 없이 처리되어야 한다.
 */
public class FieldCryptoException extends RuntimeException {

	public FieldCryptoException(String message) {
		super(message);
	}
}
