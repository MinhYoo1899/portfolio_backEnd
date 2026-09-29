package egovframework.eeum.system.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 시스템 외부연동 VO — SYSTEM_INTEGRATION 매핑.
 */
@Getter
@Setter
public class SystemIntegrationVO {

	/** 연동일련번호(PK). */
	private Long systemIntegrationSn;

	/** 연동식별코드. */
	private String integrationCd;

	/** 연동 서비스명. */
	private String integrationNm;

	/** 연동상태(CONNECTED/DISCONNECTED/ERROR). */
	private String connSttsCd;

	/** 표시순서. */
	private int sortOrder;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자. */
	private Long mdfrSn;
}
