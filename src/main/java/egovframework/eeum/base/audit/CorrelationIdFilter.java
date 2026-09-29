package egovframework.eeum.base.audit;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청당 상관관계 ID(corrId) 발급 — 감사로그·애플리케이션 로그·응답 헤더를 한 ID로 연결 (관측성 표준의 첫 조각).
 * 외부 X-Correlation-Id 신뢰/전파·비동기 전파는 추후(P1).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String MDC_KEY = "corrId";
	public static final String RESPONSE_HEADER = "X-Correlation-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String corrId = UUID.randomUUID().toString();
		MDC.put(MDC_KEY, corrId);
		response.setHeader(RESPONSE_HEADER, corrId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY); // 스레드 재사용 오염 방지 (필수)
		}
	}
}
