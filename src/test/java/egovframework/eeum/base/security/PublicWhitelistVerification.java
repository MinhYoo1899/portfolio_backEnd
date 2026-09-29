package egovframework.eeum.base.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.com.security.SecurityConfig;

/**
 * 인증 화이트리스트(공개 경로) 계약 회귀 — {@code SecurityConfig.AUTH_WHITELIST} 정적 검증.
 *
 * <p>실 필터체인 기동(무토큰 200/401 HTTP 검증)은 풀 컨텍스트가 필요해 B3 이관
 * ({@code PermissionMatrixVerification} javadoc 참조) — 여기서는 "화이트리스트에 있다/없다"를
 * 단위 레벨에서 못박고, 실 HTTP 는 dev 배포 스모크가 보완한다.
 * 핵심 회귀: ①공개 3경로 정확매칭 등재 ②/public 은 와일드카드 금지(신규 엔드포인트 자동 공개 차단)
 * ③보호 API(/availability 등)가 화이트리스트에 새지 않음.
 */
class PublicWhitelistVerification {

	private static final Set<String> EXPECTED_PUBLIC_HOUSE_PATHS = Set.of(
			"/public/houses/list",
			"/public/houses/detail",
			"/public/houses/availability");

	private static List<String> whitelist() throws Exception {
		Field field = SecurityConfig.class.getDeclaredField("AUTH_WHITELIST");
		field.setAccessible(true);
		return Arrays.asList((String[]) field.get(null));
	}

	@Test
	@DisplayName("공개 고택 3경로(list/detail/availability)가 정확매칭으로 등재 — 무토큰 접근 허용의 근거")
	void publicHousePaths_areWhitelisted() throws Exception {
		List<String> whitelist = whitelist();
		EXPECTED_PUBLIC_HOUSE_PATHS.forEach(path ->
				assertTrue(whitelist.contains(path), path + " 가 화이트리스트에 있어야 비로그인 브라우즈가 동작"));
	}

	@Test
	@DisplayName("/public 하위는 위 3경로뿐 + 와일드카드 금지(/public/** 이면 신규 엔드포인트가 자동 공개됨)")
	void publicPrefix_exactMatchOnly() throws Exception {
		List<String> publicEntries = whitelist().stream()
				.filter(p -> p.startsWith("/public")).toList();
		assertEquals(EXPECTED_PUBLIC_HOUSE_PATHS, Set.copyOf(publicEntries),
				"/public 하위 화이트리스트는 승인된 3경로 정확매칭만 허용");
		publicEntries.forEach(p -> assertFalse(p.contains("*"), "/public 경로에 와일드카드 금지: " + p));
	}

	@Test
	@DisplayName("기존 보호 API(/availability)는 미등재 — 무토큰 401 유지(전역 와일드카드도 없음)")
	void protectedAvailability_isNotWhitelisted() throws Exception {
		List<String> whitelist = whitelist();
		assertFalse(whitelist.contains("/availability"), "/availability 는 보호 API 로 유지(사용자 결정)");
		assertFalse(whitelist.contains("/**"), "전역 공개 와일드카드 금지");
	}
}
