package egovframework.eeum.base.crypto;

/**
 * 사용자계정 휴대전화번호({@code USER_ACNT.MBL_TELNO}) 전용 암호화 TypeHandler.
 *
 * <p>AAD 필드ID를 {@link #FIELD_ID} 로 고정(동결) — 컬럼 간 암호문 이식 차단(crypto-standard.md §2).
 * Spring 빈 {@link FieldEncryptor} 를 생성자로 주입받아 registry 에 인스턴스로 등록한다
 * ({@code EgovConfigAppMapper.sqlSession()}). 검색·중복판단은 이 암호화 컬럼이 아니라 {@code MBL_TELNO_HASH}
 * (HMAC, {@link FieldHasher})로만 수행한다(§2 금지규칙 1).
 */
public class MblTelnoEncryptedTypeHandler extends EncryptedStringTypeHandler {

	/** AAD 필드ID — 최초 부여 후 동결(리네임/분리되어도 변경 금지, crypto-standard §2). */
	public static final String FIELD_ID = "USER_ACNT.MBL_TELNO";

	public MblTelnoEncryptedTypeHandler(FieldEncryptor encryptor) {
		super(encryptor, FIELD_ID);
	}
}
