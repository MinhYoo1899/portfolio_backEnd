package egovframework.eeum.system.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 시스템 설정 저장 요청(전체 저장). 자동로그아웃/비번주기 허용값은 서비스에서 검증.
 */
@Schema(description = "시스템 설정 저장 요청")
public record SystemSettingUpdateRequest(

		@Schema(description = "서비스명") @NotBlank @Size(max = 100) String serviceName,

		@Schema(description = "고객센터 연락처") @Size(max = 30) String csCenterPhone,

		@Schema(description = "운영 이메일") @Email @Size(max = 200) String operationEmail,

		@Schema(description = "팩스") @Size(max = 30) String faxNumber,

		@Schema(description = "자동 로그아웃(분: 10/30/60/120)") @NotNull Integer autoLogoutMinutes,

		@Schema(description = "비밀번호 변경주기(NONE/M1/M3/M6)") @NotBlank @Size(max = 20) String passwordChangeCycleCd) {
}
