package egovframework.eeum.content.service.banner;

/**
 * 앱 노출 설정(등록/수정 라디오 — 화면정의서 CT-02) → 초기 EXPOSE_YN 매핑.
 */
public enum ContentExposeMode {

	/** 즉시 앱에 노출(EXPOSE_YN='Y'). */
	IMMEDIATE("Y"),
	/** 비활성화 저장(EXPOSE_YN='N'). */
	SAVE_INACTIVE("N");

	private final String exposeYn;

	ContentExposeMode(String exposeYn) {
		this.exposeYn = exposeYn;
	}

	/** EXPOSE_YN 컬럼값(Y/N). */
	public String toExposeYn() {
		return exposeYn;
	}
}
