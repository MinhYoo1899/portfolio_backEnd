package egovframework.eeum.base.crypto;

/**
 * 파트너 담당자 연락처({@code PRTNR_MST.PIC_MBL_TELNO}) 전용 암호화 TypeHandler.
 *
 * <p>AAD 필드ID를 {@link #FIELD_ID} 로 고정(동결) — 컬럼 간 암호문 이식 차단(crypto-standard.md §2).
 * Spring 빈 {@link FieldEncryptor} 를 생성자로 주입받아 registry 에 인스턴스로 등록한다
 * (MyBatis 가 무인자 생성하지 않도록 — {@code EgovConfigAppMapper.sqlSession()} 에서 등록).
 */
public class PicMblTelnoEncryptedTypeHandler extends EncryptedStringTypeHandler {

	/** AAD 필드ID — 최초 부여 후 동결(리네임/분리되어도 변경 금지, crypto-standard §2). */
	public static final String FIELD_ID = "PRTNR_MST.PIC_MBL_TELNO";

	public PicMblTelnoEncryptedTypeHandler(FieldEncryptor encryptor) {
		super(encryptor, FIELD_ID);
	}
}
