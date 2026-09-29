package egovframework.eeum.system.web.dto;

import egovframework.eeum.system.vo.SystemIntegrationVO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시스템 외부연동 응답. 상태는 backend 산출/저장값(fake 표시 금지).
 */
@Schema(description = "시스템 외부연동 응답")
public record SystemIntegrationResponse(
		@Schema(description = "연동식별코드") String integrationCd,
		@Schema(description = "연동 서비스명") String integrationName,
		@Schema(description = "연동상태(CONNECTED/DISCONNECTED/ERROR)") String connectionStatus) {

	public static SystemIntegrationResponse from(SystemIntegrationVO vo) {
		return new SystemIntegrationResponse(vo.getIntegrationCd(), vo.getIntegrationNm(), vo.getConnSttsCd());
	}
}
