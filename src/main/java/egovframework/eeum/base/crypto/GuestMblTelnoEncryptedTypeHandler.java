package egovframework.eeum.base.crypto;

/**
 * 예약 게스트 연락처({@code RESERVATION_GUEST.GUEST_MBL_TELNO}) 전용 암호화 TypeHandler.
 *
 * <p>AAD 필드ID를 {@link #FIELD_ID} 로 고정(동결) — 컬럼 간 암호문 이식 차단(crypto-standard.md §2).
 * {@link PicMblTelnoEncryptedTypeHandler} 와 동일 구조(전화번호 암호화, 검색 불요 — 해시 없음).
 * Spring 빈 {@link FieldEncryptor} 를 생성자로 주입받아 registry 에 인스턴스로 등록한다
 * ({@code EgovConfigAppMapper.sqlSession()} 에서 등록 — @MappedTypes 미부착으로 일반 String 무영향).
 */
public class GuestMblTelnoEncryptedTypeHandler extends EncryptedStringTypeHandler {

	/** AAD 필드ID — 최초 부여 후 동결(리네임/분리되어도 변경 금지, crypto-standard §2). */
	public static final String FIELD_ID = "RESERVATION_GUEST.GUEST_MBL_TELNO";

	public GuestMblTelnoEncryptedTypeHandler(FieldEncryptor encryptor) {
		super(encryptor, FIELD_ID);
	}
}
