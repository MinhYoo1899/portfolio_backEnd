package egovframework.com.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.auth.service.token.AuthRevocationStore;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider;
import egovframework.eeum.auth.service.token.impl.JwtTokenProvider.ParsedAccessToken;
import egovframework.eeum.base.exception.CommonErrorCode;
import egovframework.eeum.base.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT 인증 필터 — Authorization: Bearer {access} 검증 후 SecurityContext 구성.
 *
 * <p>이음 개조: 데모 LoginVO/ROLE_ADMIN 하드코딩 제거. principal = {@link AuthUser},
 * authorities = 토큰의 권한코드 목록(데이터 주도 RBAC — {@code @PreAuthorize("hasAuthority('...')")} 기준).
 * 위조/만료 토큰은 표준 envelope 으로 즉시 401 (silent fall-through 차단).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String HEADER_STRING = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	@Autowired
	private JwtTokenProvider tokenProvider;

	@Autowired
	private AuthRevocationStore authRevocationStore;

	@Autowired
	private ObjectMapper objectMapper;

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws IOException, ServletException {

		String header = req.getHeader(HEADER_STRING);
		String jwtToken = null;
		if (header != null && !header.isBlank()) {
			jwtToken = header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : header;
		}

		if (jwtToken == null || jwtToken.isBlank()) {
			chain.doFilter(req, res);
			return;
		}

		try {
			ParsedAccessToken parsed = tokenProvider.parseAccessToken(jwtToken);
			AuthUser user = parsed.principal();

			// 토큰 세대 검증 — 비밀번호 변경·탈퇴로 세대가 오르면 그 이전 세대 access 는 즉시 거부.
			// (refresh 도 같은 세대로 거부됨 → 무상태 access 의 즉시·일괄 무효화 보장)
			if (parsed.generation() < authRevocationStore.currentGeneration(user.userSn())) {
				throw new InvalidJwtException("Access token revoked by generation");
			}

			List<SimpleGrantedAuthority> authorities = user.permissions().stream()
					.map(SimpleGrantedAuthority::new)
					.toList();

			UsernamePasswordAuthenticationToken authentication =
					new UsernamePasswordAuthenticationToken(user, null, authorities);
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (InvalidJwtException e) {
			// 토큰은 존재하지만 위조·만료 — 표준 envelope 으로 즉시 401
			SecurityContextHolder.clearContext();
			res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			res.setContentType(MediaType.APPLICATION_JSON_VALUE);
			res.setCharacterEncoding("UTF-8");
			objectMapper.writeValue(res.getWriter(), ApiResponse.of(
					CommonErrorCode.UNAUTHORIZED.code(),
					CommonErrorCode.UNAUTHORIZED.defaultMessage(),
					null));
			return;
		}

		chain.doFilter(req, res);
	}
}
