package egovframework.eeum.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import egovframework.eeum.house.service.export.HouseExcelExportService;
import egovframework.eeum.house.service.house.OperatorHouseService;
import egovframework.eeum.house.web.OperatorHouseController;
import egovframework.eeum.house.web.dto.HouseReviewRejectRequest;
import egovframework.eeum.house.web.dto.HouseReviewRequest;

/**
 * 운영개시 사전점검 인가 검증 — {@code GET /operator/houses/publish-check} 는 {@code HOUSE_PROXY_MNG} 권한 필요.
 *
 * <p>{@link OperatorRoomCalendarSecurityVerification} 와 동일한 {@code @EnableMethodSecurity} 슬라이스 패턴.
 * 발행준비 점검이므로 HOUSE_VIEW(조회)로 낮추지 않고 HOUSE_PROXY_MNG(발행 액션과 동일 청중) 고정을 회귀로 잠근다.
 */
@SpringJUnitConfig(OperatorHousePublishCheckSecurityVerification.SliceConfig.class)
class OperatorHousePublishCheckSecurityVerification {

	@EnableMethodSecurity
	@Configuration
	static class SliceConfig {

		@Bean
		OperatorHouseService operatorHouseService() {
			return mock(OperatorHouseService.class);
		}

		@Bean
		HouseExcelExportService houseExcelExportService() {
			return mock(HouseExcelExportService.class);
		}

		@Bean
		OperatorHouseController operatorHouseController(OperatorHouseService operatorHouseService,
				HouseExcelExportService houseExcelExportService) {
			return new OperatorHouseController(operatorHouseService, houseExcelExportService);
		}
	}

	@Autowired
	private OperatorHouseController controller;

	@Test
	@DisplayName("컨트롤러가 메서드 보안 AOP 프록시로 보호됨")
	void controller_isSecuredProxy() {
		assertThat(AopUtils.isAopProxy(controller)).isTrue();
	}

	@Test
	@DisplayName("무인증(익명) 사전점검 → 인가 거부")
	@WithAnonymousUser
	void anonymous_denied() {
		assertThatThrownBy(() -> controller.publishCheck(5L, null)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("권한 부족(HOUSE_VIEW만, HOUSE_PROXY_MNG 없음) → 인가 거부(403) — 조회권한으로 점검 불가")
	@WithMockUser(authorities = "HOUSE_VIEW")
	void houseViewOnly_denied() {
		assertThatThrownBy(() -> controller.publishCheck(5L, null)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("HOUSE_PROXY_MNG 보유 → 사전점검 통과")
	@WithMockUser(authorities = "HOUSE_PROXY_MNG")
	void withHouseProxyMng_allowed() {
		assertThatCode(() -> controller.publishCheck(5L, null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("상세 drawer → HOUSE_VIEW 보유 시 통과(조회전용 운영자)")
	@WithMockUser(authorities = "HOUSE_VIEW")
	void drawer_withHouseView_allowed() {
		assertThatCode(() -> controller.detailDrawer(5L, null, null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("상세 drawer → HOUSE_VIEW 없으면(HOUSE_PROXY_MNG만) 인가 거부")
	@WithMockUser(authorities = "HOUSE_PROXY_MNG")
	void drawer_withoutHouseView_denied() {
		assertThatThrownBy(() -> controller.detailDrawer(5L, null, null)).isInstanceOf(AccessDeniedException.class);
	}

	// ===== 검수 전이 인가 — request=HOUSE_PROXY_MNG, approve/reject=HOUSE_REVIEW(SUPERVISOR) =====

	private static final HouseReviewRequest REVIEW_REQ = new HouseReviewRequest(5L);
	private static final HouseReviewRejectRequest REJECT_REQ = new HouseReviewRejectRequest(5L, "사진 부적합");

	@Test
	@DisplayName("검수요청 → HOUSE_PROXY_MNG 보유 시 통과")
	@WithMockUser(authorities = "HOUSE_PROXY_MNG")
	void reviewRequest_withProxyMng_allowed() {
		assertThatCode(() -> controller.reviewRequest(REVIEW_REQ, null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("검수요청 → HOUSE_REVIEW 만으로는 불가(요청은 대행권한 소관)")
	@WithMockUser(authorities = "HOUSE_REVIEW")
	void reviewRequest_withReviewOnly_denied() {
		assertThatThrownBy(() -> controller.reviewRequest(REVIEW_REQ, null)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("검수 승인/반려 → HOUSE_REVIEW 보유 시 통과")
	@WithMockUser(authorities = "HOUSE_REVIEW")
	void reviewApproveReject_withReview_allowed() {
		assertThatCode(() -> controller.reviewApprove(REVIEW_REQ, null)).doesNotThrowAnyException();
		assertThatCode(() -> controller.reviewReject(REJECT_REQ, null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("검수 승인/반려 → HOUSE_PROXY_MNG 만으로는 인가 거부(검수는 SUPERVISOR 전용)")
	@WithMockUser(authorities = "HOUSE_PROXY_MNG")
	void reviewApproveReject_withProxyMngOnly_denied() {
		assertThatThrownBy(() -> controller.reviewApprove(REVIEW_REQ, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> controller.reviewReject(REJECT_REQ, null)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("검수 전이 → 무인증(익명) 전부 거부")
	@WithAnonymousUser
	void review_anonymous_denied() {
		assertThatThrownBy(() -> controller.reviewRequest(REVIEW_REQ, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> controller.reviewApprove(REVIEW_REQ, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> controller.reviewReject(REJECT_REQ, null)).isInstanceOf(AccessDeniedException.class);
	}
}
