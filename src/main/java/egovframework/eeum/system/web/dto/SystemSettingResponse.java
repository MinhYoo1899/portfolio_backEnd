package egovframework.eeum.system.web.dto;

import java.time.Instant;
import java.time.ZoneOffset;

import egovframework.eeum.system.vo.SystemSettingVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시스템 설정 응답.
 */
@Schema(description = "시스템 설정 응답")
public record SystemSettingResponse(
		@Schema(description = "서비스명") String serviceName,
		@Schema(description = "고객센터 연락처") String csCenterPhone,
		@Schema(description = "운영 이메일") String operationEmail,
		@Schema(description = "팩스") String faxNumber,
		@Schema(description = "자동 로그아웃(분)") Integer autoLogoutMinutes,
		@Schema(description = "비밀번호 변경주기(NONE/M1/M3/M6)") String passwordChangeCycleCd,
		@Schema(description = "수정일시(UTC, ISO-8601 Z)") Instant mdfcnDt) {

	public static SystemSettingResponse from(SystemSettingVO vo) {
		return new SystemSettingResponse(
				vo.getServiceNm(),
				vo.getCsCenterTelno(),
				vo.getOprtEmlAddr(),
				vo.getFaxNo(),
				vo.getAutoLogoutMin(),
				vo.getPswdChgCycleCd(),
				vo.getMdfcnDt() == null ? null : vo.getMdfcnDt().toInstant(ZoneOffset.UTC));
	}
}
