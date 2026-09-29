package egovframework.eeum.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
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

import egovframework.eeum.reservation.service.reservation.ReservationService;
import egovframework.eeum.reservation.web.ReservationController;
import egovframework.eeum.reservation.web.dto.GuestRequest;
import egovframework.eeum.reservation.web.dto.ReservationCreateRequest;

/**
 * 예약 API(회원 채널) 인가 검증 — {@code /reservations} 의 create·confirm·detail·myList 는 모두 {@code CHNL_LOGIN_USER}(회원) 권한을 요구한다.
 *
 * <p>{@link RoomCalendarSecurityVerification} 와 동일한 {@code @EnableMethodSecurity} 슬라이스 — 컨트롤러를 프록시 빈으로 올리고
 * 서비스는 mock. 인가 거부는 {@link AccessDeniedException}(메서드 본문 진입 전 차단)으로 나타난다.
 *
 * <p><b>핵심 회귀(채널 격리)</b>: 파트너({@code CHNL_LOGIN_PARTNER})/운영자({@code CHNL_LOGIN_MASTER})가 인증되어 있어도
 * 회원 채널 자격({@code CHNL_LOGIN_USER})이 없으면 예약 API 를 쓸 수 없어야 한다.
 *
 * <p>참고: HTTP 매핑(무토큰→401 via ApiAuthenticationEntryPoint, 인증-금지→403 via ApiAccessDeniedHandler)은
 * 필터체인 관심사로 dev 스모크가 검증한다. 이 슬라이스는 {@code @PreAuthorize} 게이트 자체(본문 진입 전 차단)를 증명한다.
 */
@SpringJUnitConfig(ReservationSecurityVerification.SliceConfig.class)
class ReservationSecurityVerification {

	@EnableMethodSecurity
	@Configuration
	static class SliceConfig {

		@Bean
		ReservationService reservationService() {
			return mock(ReservationService.class);
		}

		@Bean
		ReservationController reservationController(ReservationService reservationService) {
			return new ReservationController(reservationService);
		}
	}

	@Autowired
	private ReservationController controller;

	private static final ReservationCreateRequest CREATE_REQ = new ReservationCreateRequest(
			11L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3),
			List.of(new GuestRequest("RESERVER", "홍길동", "01012345678")), null);

	/** create·confirm·detail·myList 4개 엔드포인트 모두 인가 거부되는지 단언. */
	private void assertAllDenied() {
		assertThatThrownBy(() -> controller.create(CREATE_REQ, null, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> controller.confirm(11L, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> controller.detail(11L, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> controller.myList(null)).isInstanceOf(AccessDeniedException.class);
	}

	/** 4개 엔드포인트 모두 인가 통과(메서드 본문 진입)하는지 단언. */
	private void assertAllAllowed() {
		assertThatCode(() -> controller.create(CREATE_REQ, null, null)).doesNotThrowAnyException();
		assertThatCode(() -> controller.confirm(11L, null)).doesNotThrowAnyException();
		assertThatCode(() -> controller.detail(11L, null)).doesNotThrowAnyException();
		assertThatCode(() -> controller.myList(null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("컨트롤러가 메서드 보안 AOP 프록시로 보호됨(프록시가 아니면 인가 검증이 거짓 통과)")
	void controller_isSecuredProxy() {
		assertThat(AopUtils.isAopProxy(controller)).isTrue();
	}

	@Test
	@DisplayName("회원 채널 자격(CHNL_LOGIN_USER) 보유 → 4개 엔드포인트 전부 통과")
	@WithMockUser(authorities = "CHNL_LOGIN_USER")
	void member_allAllowed() {
		assertAllAllowed();
	}

	@Test
	@DisplayName("무인증(익명) → 4개 엔드포인트 전부 차단")
	@WithAnonymousUser
	void anonymous_allDenied() {
		assertAllDenied();
	}

	@Test
	@DisplayName("파트너 채널 자격(CHNL_LOGIN_PARTNER) 단독 → 전부 차단(잘못된 채널 격리)")
	@WithMockUser(authorities = "CHNL_LOGIN_PARTNER")
	void partnerChannelAlone_allDenied() {
		assertAllDenied();
	}

	@Test
	@DisplayName("운영자 채널 자격(CHNL_LOGIN_MASTER) 단독 → 전부 차단(잘못된 채널 격리)")
	@WithMockUser(authorities = "CHNL_LOGIN_MASTER")
	void operatorChannelAlone_allDenied() {
		assertAllDenied();
	}
}
