package egovframework.eeum.auth.service.auth.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import egovframework.eeum.auth.exception.AuthErrorCode;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.exception.CommonErrorCode;

/**
 * 로그인 무차별 대입 방어({@link LoginAttemptGuard}) 회귀 검증 (B7, codex 교차검토 권고).
 *
 * <p>범위(1차 — 실제 Redis 불요): 차단 결정(계정+IP/IP 임계), 열거 방지(차단도 자격증명 오류와 동일 응답),
 * Redis 장애 시 fail-closed(503), 성공 시 계정+IP 키만 리셋을 mock 으로 검증한다.
 * 실제 Redis Lua INCR/TTL 동작·동시성은 개발서버 수동 스모크가 담당(공유 Redis — 통합 격리는 보류 결정).
 */
class LoginAttemptGuardVerification {

	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
	private final LoginAttemptGuard guard = new LoginAttemptGuard(redis);

	@BeforeEach
	void setUp() {
		// @Value 필드는 Spring 없이 생성 시 0 이므로 테스트 기본값 주입
		ReflectionTestUtils.setField(guard, "acctIpMax", 5);
		ReflectionTestUtils.setField(guard, "ipMax", 50);
		ReflectionTestUtils.setField(guard, "windowSeconds", 900L);
	}

	@Test
	@DisplayName("임계 미만 → 통과(차단 안 함)")
	void belowThreshold_notBlocked() {
		when(redis.opsForValue()).thenReturn(valueOps);
		when(valueOps.get(anyString())).thenReturn(null); // 카운터 없음 = 0

		assertThatCode(() -> guard.assertNotBlocked("user@eeum.kr", "1.1.1.1")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("계정+IP 임계 도달 → 차단(자격증명 오류와 동일 응답 — 열거 방지)")
	void acctIpThreshold_blocked() {
		when(redis.opsForValue()).thenReturn(valueOps);
		when(valueOps.get(startsWith("auth:lgnfail:ai:"))).thenReturn("5");
		when(valueOps.get(startsWith("auth:lgnfail:ip:"))).thenReturn("0");

		assertThatThrownBy(() -> guard.assertNotBlocked("user@eeum.kr", "1.1.1.1"))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
						.isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
	}

	@Test
	@DisplayName("IP 집계 임계 도달 → 차단(분산 계정 시도 방어)")
	void ipThreshold_blocked() {
		when(redis.opsForValue()).thenReturn(valueOps);
		when(valueOps.get(startsWith("auth:lgnfail:ai:"))).thenReturn("0");
		when(valueOps.get(startsWith("auth:lgnfail:ip:"))).thenReturn("50");

		assertThatThrownBy(() -> guard.assertNotBlocked("user@eeum.kr", "1.1.1.1"))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
						.isEqualTo(AuthErrorCode.AUTH_INVALID_CREDENTIALS));
	}

	@Test
	@DisplayName("Redis 장애 → fail-closed(503 SERVICE_UNAVAILABLE, 잘못된 비번처럼 보이지 않음)")
	void redisDown_failClosed() {
		when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));

		assertThatThrownBy(() -> guard.assertNotBlocked("user@eeum.kr", "1.1.1.1"))
				.isInstanceOf(BusinessException.class)
				.satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
						.isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
	}

	@Test
	@DisplayName("성공 기록 → 계정+IP 키만 삭제(IP 집계는 유지)")
	void recordSuccess_deletesAcctIpKeyOnly() {
		guard.recordSuccess("user@eeum.kr", "1.1.1.1");

		verify(redis).delete(startsWith("auth:lgnfail:ai:"));
		verify(redis, never()).delete(startsWith("auth:lgnfail:ip:"));
	}

	@Test
	@DisplayName("실패 기록 → 계정+IP·IP 두 키 모두 원자 증가(INCR+TTL) 스크립트 호출(2회)")
	void recordFailure_incrementsBothKeys() {
		guard.recordFailure("user@eeum.kr", "1.1.1.1");

		verify(redis, times(2)).execute(
				ArgumentMatchers.<RedisScript<Long>>any(),
				ArgumentMatchers.<String>anyList(),
				ArgumentMatchers.any());
	}
}
