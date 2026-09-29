package egovframework.eeum.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 로그(LOGIN_LOG_INFO) 기록 VO — append-only.
 */
@Getter
@Builder
public class LoginLogInfoVO {

	/** 사용자일련번호 (미존재 계정 시도 등은 NULL) */
	private final Long userSn;

	/** 로그인ID(이메일) */
	private final String lgnId;

	/** 로그인유형 (EMAIL/SOCIAL) */
	private final String lgnType;

	/** 처리결과 (SUCCESS/FAIL) */
	private final String prcsRslt;

	/** 실패사유 */
	private final String failRsn;

	/** 접속IP */
	private final String cntnIp;

	/** 사용자에이전트 */
	private final String userAgnt;
}
