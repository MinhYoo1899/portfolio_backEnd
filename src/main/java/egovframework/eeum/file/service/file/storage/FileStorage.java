package egovframework.eeum.file.service.file.storage;

import java.io.InputStream;

/**
 * 파일 저장소 추상화 — 저장 위치(SFTP/로컬/오브젝트스토리지)를 교체 가능하게 둔다.
 *
 * <p>키({@code relativeKey})는 base 경로를 포함하지 않는 환경독립 상대키
 * (예: {@code public/202606/abcd....png}). base 결합은 구현체가 설정으로만 수행한다.
 */
public interface FileStorage {

	/** 저장소유형코드(ATCH_FILE.STRG_TYPE_CD 에 기록 — 예: SFTP). */
	String storageTypeCode();

	/** 설정이 갖춰져 사용 가능한지(미설정이면 호출 측이 503 으로 처리). */
	boolean isAvailable();

	/**
	 * 상대키 위치에 저장. 임시 업로드 후 원자적 rename 으로 부분파일 노출을 막는다.
	 * 입력 스트림은 호출자가 연 것으로, 본 메서드는 닫지 않는다(try-with-resources 는 호출 측).
	 */
	void store(String relativeKey, InputStream content);

	/** 상대키 스트림 열기 — 반환된 {@link StoredResource#close()} 로 자원(채널/세션) 반납 필수. */
	StoredResource open(String relativeKey);

	/** 상대키 삭제(없어도 예외를 던지지 않음 — 멱등). */
	void delete(String relativeKey);
}
