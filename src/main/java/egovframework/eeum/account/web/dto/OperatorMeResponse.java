package egovframework.eeum.account.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import egovframework.eeum.account.vo.OperatorVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영자 본인 정보 응답 DTO ({@code GET /operator/me}) — "내 정보" 화면용.
 *
 * <p>대상 = 토큰의 본인. 연락처는 <b>본인 데이터이므로 전체 노출</b>(정규화된 숫자값 — crypto-standard.md §7의
 * "전체 노출은 권한 필요 화면" 중 본인 자기조회). 타인 조회(목록/상세)는 마스킹.
 */
@Schema(description = "운영자 본인 정보")
public record OperatorMeResponse(
		@Schema(description = "사용자일련번호(문자열)") String userSn,
		@Schema(description = "로그인 이메일") String emlAddr,
		@Schema(description = "운영자명") String userNm,
		@Schema(description = "계정상태") String acntStts,
		@Schema(description = "부서명") String deptNm,
		@Schema(description = "직위명") String jbpsNm,
		@Schema(description = "휴대전화번호(본인 — 전체)", example = "01012345678") String mblTelno,
		@Schema(description = "보유 역할코드 목록") List<String> roleCds,
		@Schema(description = "가입일시(UTC, ISO-8601 Z)") Instant regDt) {

	public static OperatorMeResponse from(OperatorVO vo, List<String> roleCds) {
		return new OperatorMeResponse(
				String.valueOf(vo.getUserSn()),
				vo.getEmlAddr(),
				vo.getUserNm(),
				vo.getAcntStts(),
				vo.getDeptNm(),
				vo.getJbpsNm(),
				vo.getMblTelno(),
				roleCds == null ? List.of() : List.copyOf(roleCds),
				vo.getRegDt() == null ? null : vo.getRegDt().toInstant(ZoneOffset.UTC));
	}
}
