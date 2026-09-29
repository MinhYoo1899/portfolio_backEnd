package egovframework.eeum.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

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

import egovframework.eeum.account.service.partner.PartnerService;
import egovframework.eeum.account.web.PartnerController;
import egovframework.eeum.account.web.PartnerSelfController;
import egovframework.eeum.account.web.dto.PartnerApproveRequest;
import egovframework.eeum.account.web.dto.PartnerRejectRequest;
import egovframework.eeum.account.web.dto.PartnerSearchRequest;
import egovframework.eeum.account.web.dto.PartnerUpdateRequest;
import egovframework.eeum.auth.vo.AuthUser;

/**
 * 권한코드 기반 인가(@PreAuthorize hasAuthority) 매트릭스 회귀 검증 (B2, codex 교차검토 권고) —
 * 수동 8/8 검증 중 "권한 결정" 부분을 코드화한다. 권한 없음→거부 / 권한 보유→통과 / 조회·처리 권한 분리(V8).
 *
 * <p>대상(PartnerController): 조회 detail·list = {@code PRTNR_VIEW}, 처리 approve·reject = {@code PRTNR_APRV}.
 * 인가 거부는 {@link AccessDeniedException} 로 나타난다(메서드 본문 진입 전 차단 — 음성 케이스가 본문 예외가 아니라
 * AccessDeniedException 을 기대하는 것이 그 증거). 실제 MVC 경로에서 이 예외는 GlobalExceptionHandler 가 403 envelope 로,
 * 필터 단계 거부는 ApiAccessDeniedHandler 가 403 envelope 로 변환한다(그 변환은 각각의 단위 검증이 담당).
 *
 * <p>범위(1차 — 웹/egov/DB 불요): {@code @EnableMethodSecurity} 슬라이스에서 컨트롤러를 프록시 빈으로 띄워
 * 메서드 보안 어드바이스만 검증한다(서비스는 mock). 인증 주체는 spring-security-test 로 주입한다(토큰 발급/파싱 우회).
 *
 * <p><b>B3 이관(풀 컨텍스트/Testcontainers 이후)</b>: URL 화이트리스트(무인증 401 엔트리포인트, {@code /menus/list} 공개 노출),
 * {@code @AuthenticationPrincipal} 바인딩, 실제 JWT 토큰 파싱, DB 권한 데이터 기반 메뉴 노출 행 계산은 실제
 * {@code SecurityConfig} 필터체인을 띄워 검증한다(egov 공통 컴포넌트 의존으로 @WebMvcTest 슬라이스가 불안정 — 풀 컨텍스트 필요).
 *
 * <p>핵심 회귀(V8 운영자 2단계 RBAC): 조회 권한(PRTNR_VIEW)만 가진 직원은 승인·반려(PRTNR_APRV)에서 거부되어야 한다.
 */
@SpringJUnitConfig(PermissionMatrixVerification.MethodSecuritySliceConfig.class)
class PermissionMatrixVerification {

	/** 메서드 보안만 활성화한 최소 슬라이스 — 컨트롤러를 프록시 빈으로 올리고 서비스는 mock 으로 대체. */
	@EnableMethodSecurity
	@Configuration
	static class MethodSecuritySliceConfig {

		@Bean
		PartnerService partnerService() {
			return mock(PartnerService.class);
		}

		@Bean
		PartnerController partnerController(PartnerService partnerService) {
			return new PartnerController(partnerService);
		}

		@Bean
		PartnerSelfController partnerSelfController(PartnerService partnerService) {
			return new PartnerSelfController(partnerService);
		}
	}

	@Autowired
	private PartnerController partnerController;

	@Autowired
	private PartnerSelfController partnerSelfController;

	private static final PartnerSearchRequest SEARCH_REQUEST = new PartnerSearchRequest();
	private static final PartnerApproveRequest APPROVE_REQUEST = new PartnerApproveRequest("1");
	private static final PartnerRejectRequest REJECT_REQUEST = new PartnerRejectRequest("1", "서류 미비");
	private static final PartnerUpdateRequest UPDATE_REQUEST = new PartnerUpdateRequest("1", "상호수정", null, null, null, null, null, null, null, null);
	private static final AuthUser SUPERVISOR = new AuthUser(1L, "spv@eeum.dev", "수퍼바이저",
			"OPERATOR", "ACTIVE", List.of("PRTNR_VIEW", "PRTNR_APRV"));
	private static final AuthUser PARTNER = new AuthUser(100L, "p@eeum.kr", "파트너",
			"PARTNER", "ACTIVE", List.of("HOUSE_MNG"));

	@Test
	@DisplayName("컨트롤러가 메서드 보안 AOP 프록시로 보호됨(프록시가 아니면 모든 인가 검증이 거짓 통과)")
	void controller_isSecuredProxy() {
		assertThat(AopUtils.isAopProxy(partnerController)).isTrue();
	}

	// --- 조회 권한(PRTNR_VIEW): detail / list ---

	@Test
	@DisplayName("무인증(익명) 조회 → 인가 거부")
	@WithAnonymousUser
	void anonymous_cannotRead_denied() {
		assertThatThrownBy(() -> partnerController.detail("1")).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> partnerController.list(SEARCH_REQUEST)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> partnerController.pendingCount()).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("권한 부족(PRTNR_VIEW 없음) 조회 → 인가 거부(403)")
	@WithMockUser(authorities = "SOME_OTHER")
	void withoutAuthority_cannotRead_denied() {
		assertThatThrownBy(() -> partnerController.detail("1")).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> partnerController.list(SEARCH_REQUEST)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> partnerController.pendingCount()).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("PRTNR_VIEW 보유 → 조회(detail·list) 통과")
	@WithMockUser(authorities = "PRTNR_VIEW")
	void withPrtnrView_canRead() {
		assertThatCode(() -> partnerController.detail("1")).doesNotThrowAnyException();
		assertThatCode(() -> partnerController.list(SEARCH_REQUEST)).doesNotThrowAnyException();
		assertThatCode(() -> partnerController.pendingCount()).doesNotThrowAnyException();
	}

	// --- 처리 권한(PRTNR_APRV): approve / reject — 조회·처리 분리(V8) ---

	@Test
	@DisplayName("핵심 회귀(V8): 조회 권한(PRTNR_VIEW)만 가진 직원의 승인·반려(PRTNR_APRV) → 인가 거부(403)")
	@WithMockUser(authorities = "PRTNR_VIEW")
	void viewOnlyStaff_cannotProcess_denied() {
		assertThatThrownBy(() -> partnerController.approve(SUPERVISOR, APPROVE_REQUEST))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> partnerController.reject(SUPERVISOR, REJECT_REQUEST))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("PRTNR_APRV 보유(수퍼바이저) → 승인·반려 통과")
	@WithMockUser(authorities = { "PRTNR_VIEW", "PRTNR_APRV" })
	void supervisor_canProcess() {
		assertThatCode(() -> partnerController.approve(SUPERVISOR, APPROVE_REQUEST)).doesNotThrowAnyException();
		assertThatCode(() -> partnerController.reject(SUPERVISOR, REJECT_REQUEST)).doesNotThrowAnyException();
	}

	// --- 정보 수정(update) — 처리 권한(PRTNR_APRV) ---

	@Test
	@DisplayName("조회 권한(PRTNR_VIEW)만 가진 직원의 정보수정(PRTNR_APRV) → 인가 거부(403)")
	@WithMockUser(authorities = "PRTNR_VIEW")
	void viewOnlyStaff_cannotUpdate_denied() {
		assertThatThrownBy(() -> partnerController.update(SUPERVISOR, UPDATE_REQUEST))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("PRTNR_APRV 보유(수퍼바이저) → 정보수정 통과")
	@WithMockUser(authorities = { "PRTNR_VIEW", "PRTNR_APRV" })
	void supervisor_canUpdate() {
		assertThatCode(() -> partnerController.update(SUPERVISOR, UPDATE_REQUEST)).doesNotThrowAnyException();
	}

	// --- 파트너 본인 /partner/me — HOUSE_MNG 전용(운영자/회원 권한과 혼동 없음) ---

	@Test
	@DisplayName("HOUSE_MNG 보유(파트너 본인) → GET /partner/me 통과")
	@WithMockUser(authorities = "HOUSE_MNG")
	void partner_canReadSelf() {
		assertThatCode(() -> partnerSelfController.me(PARTNER)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("무인증(익명) → /partner/me 인가 거부")
	@WithAnonymousUser
	void anonymous_cannotReadSelf_denied() {
		assertThatThrownBy(() -> partnerSelfController.me(PARTNER)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("운영자 권한(PRTNR_VIEW)만으로는 /partner/me 접근 불가(권한 혼동 없음)")
	@WithMockUser(authorities = "PRTNR_VIEW")
	void operatorAuthority_cannotReadSelf_denied() {
		assertThatThrownBy(() -> partnerSelfController.me(PARTNER)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("일반회원 권한(MEMBER_VIEW)으로는 /partner/me 접근 불가")
	@WithMockUser(authorities = "MEMBER_VIEW")
	void memberAuthority_cannotReadSelf_denied() {
		assertThatThrownBy(() -> partnerSelfController.me(PARTNER)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("대행 권한(HOUSE_PROXY_MNG) 단독으로는 /partner/me 접근 불가(본인 API ≠ 대행)")
	@WithMockUser(authorities = "HOUSE_PROXY_MNG")
	void houseProxyMng_cannotReadSelf_denied() {
		assertThatThrownBy(() -> partnerSelfController.me(PARTNER)).isInstanceOf(AccessDeniedException.class);
	}
}
