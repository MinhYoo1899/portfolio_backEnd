package egovframework.eeum.account.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 생성/비밀번호초기화 결과 DTO — 생성된 userSn + 1회성 설정토큰/설정URL.
 *
 * <p>설정토큰 원문은 이 응답에서 1회만 노출된다(서버는 해시만 저장). 로그/감사에는 남기지 않는다.
 */
@Schema(description = "운영자 생성/설정토큰 발급 결과(1회성 토큰 포함)")
public record OperatorCreatedResponse(
		@Schema(description = "생성/대상 사용자일련번호(문자열)") String userSn,
		@Schema(description = "1회성 설정토큰(원문 — 재조회 불가)") String setupToken,
		@Schema(description = "설정 URL(setup-password 화면)") String setupUrl) {

	public static OperatorCreatedResponse of(long userSn, String setupToken, String setupUrl) {
		return new OperatorCreatedResponse(String.valueOf(userSn), setupToken, setupUrl);
	}
}
