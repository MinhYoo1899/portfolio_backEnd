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

import egovframework.eeum.house.service.calendar.RoomCalendarService;
import egovframework.eeum.house.web.OperatorRoomCalendarController;
import egovframework.eeum.house.web.dto.RoomCalendarCloseRequest;
import egovframework.eeum.house.web.dto.RoomCalendarPriceStockSetRequest;
import egovframework.eeum.house.web.dto.RoomCalendarSearchRequest;
import egovframework.eeum.house.web.dto.RoomCalendarSetRequest;

/**
 * 운영자 대행 캘린더 인가 검증 — {@code /operator/rooms/calendar} 의 set·price-stock/set·close·list 4개 엔드포인트는
 * 모두 {@code HOUSE_PROXY_MNG} 권한을 요구한다.
 *
 * <p>{@link RoomCalendarSecurityVerification}(파트너 채널, HOUSE_MNG)와 대칭. {@link PermissionMatrixVerification} 와
 * 동일한 {@code @EnableMethodSecurity} 슬라이스 패턴 — 컨트롤러를 프록시 빈으로 올리고 서비스는 mock.
 * 인가 거부는 {@link AccessDeniedException}(메서드 본문 진입 전 차단)으로 나타난다.
 *
 * <p>핵심 회귀: 파트너 권한({@code HOUSE_MNG}) 단독으로는 대행 캘린더를 조작할 수 없어야 한다(채널 격리).
 */
@SpringJUnitConfig(OperatorRoomCalendarSecurityVerification.SliceConfig.class)
class OperatorRoomCalendarSecurityVerification {

	@EnableMethodSecurity
	@Configuration
	static class SliceConfig {

		@Bean
		RoomCalendarService roomCalendarService() {
			return mock(RoomCalendarService.class);
		}

		@Bean
		OperatorRoomCalendarController operatorRoomCalendarController(RoomCalendarService roomCalendarService) {
			return new OperatorRoomCalendarController(roomCalendarService);
		}
	}

	@Autowired
	private OperatorRoomCalendarController controller;

	private static final RoomCalendarSearchRequest REQ =
			new RoomCalendarSearchRequest(11L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
	private static final RoomCalendarPriceStockSetRequest PS_REQ =
			new RoomCalendarPriceStockSetRequest(11L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null, 1, 150000);
	private static final RoomCalendarSetRequest SET_REQ =
			new RoomCalendarSetRequest(11L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), null, 1, 150000, "N");
	private static final RoomCalendarCloseRequest CLOSE_REQ =
			new RoomCalendarCloseRequest(11L, List.of(LocalDate.of(2026, 7, 1)), "Y", null);

	/** set·price-stock/set·close·list 4개 엔드포인트 모두 인가 거부되는지 단언. */
	private void assertAllDenied() {
		assertThatThrownBy(() -> controller.list(REQ, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> controller.setRange(SET_REQ, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> controller.close(CLOSE_REQ, null)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> controller.setPriceStock(PS_REQ, null)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("컨트롤러가 메서드 보안 AOP 프록시로 보호됨(프록시가 아니면 인가 검증이 거짓 통과)")
	void controller_isSecuredProxy() {
		assertThat(AopUtils.isAopProxy(controller)).isTrue();
	}

	@Test
	@DisplayName("무인증(익명) 캘린더 조회 → 인가 거부")
	@WithAnonymousUser
	void anonymous_cannotRead_denied() {
		assertThatThrownBy(() -> controller.list(REQ, null)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("권한 부족(HOUSE_PROXY_MNG 없음) → 인가 거부(403)")
	@WithMockUser(authorities = "SOME_OTHER")
	void withoutAuthority_cannotRead_denied() {
		assertThatThrownBy(() -> controller.list(REQ, null)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("HOUSE_PROXY_MNG 보유 → 캘린더 조회 통과")
	@WithMockUser(authorities = "HOUSE_PROXY_MNG")
	void withHouseProxyMng_canRead() {
		assertThatCode(() -> controller.list(REQ, null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("권한 부족(HOUSE_PROXY_MNG 없음) → 가격/수량 설정 인가 거부")
	@WithMockUser(authorities = "SOME_OTHER")
	void withoutAuthority_cannotSetPriceStock_denied() {
		assertThatThrownBy(() -> controller.setPriceStock(PS_REQ, null)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@DisplayName("HOUSE_PROXY_MNG 보유 → 가격/수량 설정 통과")
	@WithMockUser(authorities = "HOUSE_PROXY_MNG")
	void withHouseProxyMng_canSetPriceStock() {
		assertThatCode(() -> controller.setPriceStock(PS_REQ, null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("HOUSE_PROXY_MNG 보유 → 설정(set)·마감(close)도 통과(누락 보강)")
	@WithMockUser(authorities = "HOUSE_PROXY_MNG")
	void withHouseProxyMng_canSetRangeAndClose() {
		assertThatCode(() -> controller.setRange(SET_REQ, null)).doesNotThrowAnyException();
		assertThatCode(() -> controller.close(CLOSE_REQ, null)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("파트너 권한(HOUSE_MNG) 단독 → 4개 엔드포인트 전부 차단(대행 ≠ 본인, 채널 격리)")
	@WithMockUser(authorities = "HOUSE_MNG")
	void houseMngAlone_allDenied() {
		assertAllDenied();
	}

	@Test
	@DisplayName("운영자 조회권한(HOUSE_VIEW) 단독 → 4개 엔드포인트 전부 차단")
	@WithMockUser(authorities = "HOUSE_VIEW")
	void houseViewAlone_allDenied() {
		assertAllDenied();
	}

	@Test
	@DisplayName("일반회원 권한(MEMBER_VIEW) → 4개 엔드포인트 전부 차단")
	@WithMockUser(authorities = "MEMBER_VIEW")
	void memberView_allDenied() {
		assertAllDenied();
	}

	@Test
	@DisplayName("무인증(익명) → 4개 엔드포인트 전부 차단")
	@WithAnonymousUser
	void anonymous_allDenied() {
		assertAllDenied();
	}
}
