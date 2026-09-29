package egovframework.eeum.system.vo;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 시스템 설정 VO — SYSTEM_SETTING 매핑(단일행, SN=1).
 */
@Getter
@Setter
public class SystemSettingVO {

	/** 설정일련번호(단일행=1). */
	private Long systemSettingSn;

	/** 서비스명. */
	private String serviceNm;

	/** 고객센터 연락처. */
	private String csCenterTelno;

	/** 운영 이메일. */
	private String oprtEmlAddr;

	/** 팩스. */
	private String faxNo;

	/** 자동 로그아웃(분). */
	private Integer autoLogoutMin;

	/** 비밀번호 변경주기(NONE/M1/M3/M6). */
	private String pswdChgCycleCd;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자. */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자. */
	private Long mdfrSn;
}
