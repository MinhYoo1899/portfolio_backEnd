package egovframework.eeum.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 멱등키(Idempotency-Key) 필터 — 생성(insert) API 의 중복 제출/재시도를 안전하게 만드는 opt-in 방어선.
 *
 * <p>동작 조건(모두 성립 시에만): {@code Idempotency-Key} 헤더 존재 + POST + 보호경로(설정) + 인증된 {@link AuthUser} principal.
 * 하나라도 아니면 순수 passthrough(Redis 미접근, 기존 동작 무변경). JWT 인증 필터 뒤에 등록되어 SecurityContext 가 채워진 상태로 실행된다.
 *
 * <p>흐름: 본문해시 계산 → claim(선점) → (획득) 컨트롤러 실행 후 2xx=promote(DONE)/그외=release, 항상 원본 응답 복사;
 * (미획득) find 후 본문해시 불일치=409 REUSED / PENDING=409 IN_PROGRESS / DONE=응답 재생. 저장소 장애는 fail-closed(503).
 *
 * <p><b>알려진 한계</b>: 소유 요청이 응답을 낸 뒤 promote/release 가 Redis 장애로 실패하면 경고 로그만 남기고
 * 이미 생성된 응답은 그대로 반환한다(응답을 되돌리지 않음). 남은 PENDING 은 pending-ttl 만료로 자연 회수된다.
 */
@Slf4j
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

	/** 멱등키 요청 헤더명. */
	public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

	private static final String METHOD_POST = "POST";
	private static final String CHANNEL_OPERATOR = "OPERATOR";
	private static final String CHANNEL_PARTNER = "PARTNER";
	private static final String CHANNEL_MEMBER = "MEMBER";
	private static final String OPERATOR_PATH_PREFIX = "/operator/";
	private static final String MEMBER_PATH_PREFIX = "/reservations";

	private final IdempotencyStore store;
	private final IdempotencyProperties properties;
	private final ObjectMapper objectMapper;

	public IdempotencyFilter(IdempotencyStore store, IdempotencyProperties properties, ObjectMapper objectMapper) {
		this.store = store;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		String idempotencyKey = request.getHeader(HEADER_IDEMPOTENCY_KEY);
		String routePath = resolveRoutePath(request);

		// opt-in 조건 — 하나라도 아니면 순수 passthrough(저장소 미접근, 기존 동작 무변경).
		if (idempotencyKey == null || idempotencyKey.isBlank()
				|| !METHOD_POST.equalsIgnoreCase(request.getMethod())
				|| !properties.isGuarded(routePath)) {
			chain.doFilter(request, response);
			return;
		}
		AuthUser principal = currentAuthUser();
		if (principal == null) {
			chain.doFilter(request, response);
			return;
		}

		// 본문해시 — 원본 바이트 SHA-256. 래핑으로 하위 컨트롤러도 같은 본문을 재독할 수 있게 한다.
		CachedBodyHttpServletRequestWrapper wrappedRequest = new CachedBodyHttpServletRequestWrapper(request);
		String bodyHash = sha256Hex(wrappedRequest.getCachedBody());
		String channel = resolveChannel(routePath);
		String key = buildKey(principal.userSn(), channel, METHOD_POST, routePath, idempotencyKey);

		boolean acquired;
		try {
			acquired = store.claim(key, bodyHash, properties.pendingTtl());
		} catch (IdempotencyInfraException e) {
			// 저장소 불확실 → fail-closed(중복 생성 방지). 컨트롤러로 진행하지 않는다.
			log.warn("[IDEMPOTENCY] claim 실패 — fail-closed 503 (key={})", key, e);
			writeError(response, IdempotencyErrorCode.IDEMPOTENCY_UNAVAILABLE);
			return;
		}

		if (acquired) {
			handleOwner(wrappedRequest, response, chain, key, bodyHash);
		} else {
			handleReplay(response, key, bodyHash);
		}
	}

	/** 선점 획득 — 이 요청이 소유. 컨트롤러 실행 후 결과에 따라 승격/해제하고 항상 원본 응답을 복사한다. */
	private void handleOwner(CachedBodyHttpServletRequestWrapper request, HttpServletResponse response,
			FilterChain chain, String key, String bodyHash) throws ServletException, IOException {

		ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
		boolean success2xx = false;
		try {
			chain.doFilter(request, responseWrapper);
			int status = responseWrapper.getStatus();
			success2xx = status >= 200 && status <= 299;
			if (success2xx) {
				promoteQuietly(key, buildDoneRecord(bodyHash, responseWrapper));
			}
		} finally {
			if (!success2xx) {
				// 비2xx(또는 예외로 중단) → 선점 해제(재시도 허용).
				releaseQuietly(key);
			}
			responseWrapper.copyBodyToResponse(); // 항상 원본 응답으로 복사(캐시 버퍼 flush)
		}
	}

	/** 선점 미획득 — 기존 레코드를 조회해 재사용/진행중/재생을 판정한다. */
	private void handleReplay(HttpServletResponse response, String key, String bodyHash) throws IOException {
		Optional<IdempotencyRecord> found;
		try {
			found = store.find(key);
		} catch (IdempotencyInfraException e) {
			log.warn("[IDEMPOTENCY] find 실패 — fail-closed 503 (key={})", key, e);
			writeError(response, IdempotencyErrorCode.IDEMPOTENCY_UNAVAILABLE);
			return;
		}
		if (found.isEmpty()) {
			// 선점 직후 만료된 드문 경합 — 진행중으로 간주(잠시 후 재시도).
			writeError(response, IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS);
			return;
		}
		IdempotencyRecord record = found.get();
		if (!bodyHash.equals(record.bodyHash())) {
			writeError(response, IdempotencyErrorCode.IDEMPOTENCY_KEY_REUSED);
			return;
		}
		if (IdempotencyRecord.STATE_PENDING.equals(record.state())) {
			writeError(response, IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS);
			return;
		}
		// DONE — 저장된 응답 스냅샷을 그대로 재생(컨트롤러 미실행).
		replay(response, record);
	}

	/** 완료 응답 스냅샷 레코드 구성 — allowlist 헤더만 담고 Content-Type 은 별도 필드로 보존. */
	private IdempotencyRecord buildDoneRecord(String bodyHash, ContentCachingResponseWrapper responseWrapper) {
		Map<String, String> headers = new LinkedHashMap<>();
		for (String name : properties.replayHeaders()) {
			String value = responseWrapper.getHeader(name);
			if (value != null) {
				headers.put(name, value);
			}
		}
		String body = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
		return IdempotencyRecord.done(bodyHash, responseWrapper.getStatus(),
				responseWrapper.getContentType(), headers, body);
	}

	/** DONE 레코드 재생 — 상태/Content-Type/allowlist 헤더/본문을 응답에 그대로 기록. */
	private void replay(HttpServletResponse response, IdempotencyRecord record) throws IOException {
		if (record.httpStatus() != null) {
			response.setStatus(record.httpStatus());
		}
		if (record.contentType() != null) {
			response.setContentType(record.contentType());
		}
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		record.headers().forEach(response::setHeader);
		if (record.responseBody() != null) {
			response.getWriter().write(record.responseBody());
		}
	}

	/** promote 실패는 이미 생성된 응답을 되돌리지 않는다(경고만) — 남은 PENDING 은 TTL 로 자연 회수. */
	private void promoteQuietly(String key, IdempotencyRecord doneRecord) {
		try {
			store.promote(key, doneRecord, properties.doneTtl());
		} catch (IdempotencyInfraException e) {
			log.warn("[IDEMPOTENCY] promote 실패 — 응답은 이미 생성됨(무시), PENDING TTL 로 회수 (key={})", key, e);
		}
	}

	/** release 실패도 응답에 영향 주지 않는다(경고만) — 남은 PENDING 은 TTL 로 자연 회수. */
	private void releaseQuietly(String key) {
		try {
			store.release(key);
		} catch (IdempotencyInfraException e) {
			log.warn("[IDEMPOTENCY] release 실패 — PENDING TTL 로 회수 (key={})", key, e);
		}
	}

	/** 오류 응답 직접 기록 — JwtAuthenticationFilter 와 동일 패턴(envelope JSON + status + UTF-8). */
	private void writeError(HttpServletResponse response, IdempotencyErrorCode code) throws IOException {
		response.setStatus(code.httpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(),
				ApiResponse.of(code.code(), code.defaultMessage(), null));
	}

	/** SecurityContext 의 principal 이 {@link AuthUser} 면 반환, 아니면(비인증/익명) null. */
	private static AuthUser currentAuthUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return null;
		}
		Object principal = authentication.getPrincipal();
		return principal instanceof AuthUser authUser ? authUser : null;
	}

	/**
	 * 채널 분류(관측용) — 키는 이미 userSn 으로 스코프됨. {@code /operator/}=OPERATOR, {@code /reservations}=MEMBER, 그 외=PARTNER.
	 * (파트너 insert 계열이 기본 PARTNER, 손님 예약이 MEMBER — 로그·키 가독성만 결정하고 격리 자체는 userSn 이 담당.)
	 */
	private static String resolveChannel(String routePath) {
		if (routePath.startsWith(OPERATOR_PATH_PREFIX)) {
			return CHANNEL_OPERATOR;
		}
		if (routePath.startsWith(MEMBER_PATH_PREFIX)) {
			return CHANNEL_MEMBER;
		}
		return CHANNEL_PARTNER;
	}

	/** 컨텍스트 경로를 제거한 서블릿 경로(쿼리스트링 미포함). */
	private static String resolveRoutePath(HttpServletRequest request) {
		String contextPath = request.getContextPath();
		String uri = request.getRequestURI();
		if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
			return uri.substring(contextPath.length());
		}
		return uri;
	}

	/** Redis 키: {@code idem:v1:{userSn}:{channel}:{method}:{routePath}:{idempotencyKey}}. (본문해시는 값에만) */
	static String buildKey(long userSn, String channel, String method, String routePath, String idempotencyKey) {
		return "idem:v1:" + userSn + ":" + channel + ":" + method + ":" + routePath + ":" + idempotencyKey;
	}

	/** 원본 본문 바이트의 SHA-256 hex(canonical JSON 아님). */
	static String sha256Hex(byte[] body) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(body));
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 은 JVM 표준 — 실질적으로 도달 불가.
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
		}
	}
}
