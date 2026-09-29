package egovframework.eeum.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 멱등 필터 검증 — MockMvc standalone + 더미 컨트롤러 + 모킹된 {@link IdempotencyStore}.
 * 헤더 없음 passthrough / 최초 promote / 재생 / 키재사용 / 진행중 / 저장소 장애 503 / 비2xx release / 키 스코프 분리를 고정한다.
 */
class IdempotencyFilterVerification {

	private static final String KEY_HEADER = IdempotencyFilter.HEADER_IDEMPOTENCY_KEY;
	private static final String BODY = "{\"a\":1}";

	private IdempotencyStore store;
	private ProbeController probe;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		store = mock(IdempotencyStore.class);
		probe = new ProbeController();
		IdempotencyFilter filter = new IdempotencyFilter(store, properties(), new ObjectMapper());
		mockMvc = MockMvcBuilders.standaloneSetup(probe).addFilter(filter).build();

		AuthUser user = new AuthUser(7L, "partner@eeum.com", "파트너", "PARTNER", "ACTIVE", List.of("HOUSE_MNG"));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(user, null, List.of()));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private static byte[] bodyBytes() {
		return BODY.getBytes(StandardCharsets.UTF_8);
	}

	/** @Value 필드를 반사로 채운 뒤 @PostConstruct 검증까지 수행한 실제 설정 인스턴스. */
	private static IdempotencyProperties properties() {
		IdempotencyProperties p = new IdempotencyProperties();
		ReflectionTestUtils.setField(p, "pendingTtlSeconds", 60L);
		ReflectionTestUtils.setField(p, "doneTtlSeconds", 86400L);
		ReflectionTestUtils.setField(p, "guardedPathsRaw", new String[] {
				"/houses/insert", "/operator/houses/insert", "/rooms/insert", "/operator/rooms/insert", "/reservations"});
		ReflectionTestUtils.setField(p, "replayHeadersRaw", new String[] {"Location"});
		p.validateConfig();
		return p;
	}

	@Test
	@DisplayName("(1)(6-b) 헤더 없음 → passthrough(컨트롤러 1회 호출, 저장소 미접근)")
	void noHeader_passthrough() throws Exception {
		mockMvc.perform(post("/houses/insert")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isCreated());
		assertEquals(1, probe.calls.get());
		verifyNoInteractions(store);
	}

	@Test
	@DisplayName("(2) 최초 요청 → 201 + promote(DONE) 호출(상태/본문해시/키)")
	void firstCall_promotesDone() throws Exception {
		when(store.claim(anyString(), anyString(), any())).thenReturn(true);

		mockMvc.perform(post("/houses/insert")
						.header(KEY_HEADER, "k-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isCreated());
		assertEquals(1, probe.calls.get());

		ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<IdempotencyRecord> recCap = ArgumentCaptor.forClass(IdempotencyRecord.class);
		verify(store).promote(keyCap.capture(), recCap.capture(), any());
		IdempotencyRecord done = recCap.getValue();
		assertEquals(IdempotencyRecord.STATE_DONE, done.state());
		assertEquals(201, done.httpStatus());
		assertEquals(IdempotencyFilter.sha256Hex(bodyBytes()), done.bodyHash());
		assertEquals("idem:v1:7:PARTNER:POST:/houses/insert:k-1", keyCap.getValue());
	}

	@Test
	@DisplayName("(3) 같은 키 + 같은 본문 → 재생(201, 본문/Content-Type 동일, 컨트롤러 미호출)")
	void sameKeySameBody_replays() throws Exception {
		IdempotencyRecord done = IdempotencyRecord.done(IdempotencyFilter.sha256Hex(bodyBytes()), 201,
				"application/json", Map.of(), "{\"resultCode\":\"SUCCESS\"}");
		when(store.claim(anyString(), anyString(), any())).thenReturn(false);
		when(store.find(anyString())).thenReturn(Optional.of(done));

		mockMvc.perform(post("/houses/insert")
						.header(KEY_HEADER, "k-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().string("{\"resultCode\":\"SUCCESS\"}"));
		assertEquals(0, probe.calls.get());
	}

	@Test
	@DisplayName("(4) 같은 키 + 다른 본문 → 409 IDEMPOTENCY_KEY_REUSED(컨트롤러 미호출)")
	void sameKeyDifferentBody_conflict() throws Exception {
		IdempotencyRecord done = IdempotencyRecord.done("DIFFERENT_HASH", 201,
				"application/json", Map.of(), "{\"resultCode\":\"SUCCESS\"}");
		when(store.claim(anyString(), anyString(), any())).thenReturn(false);
		when(store.find(anyString())).thenReturn(Optional.of(done));

		mockMvc.perform(post("/houses/insert")
						.header(KEY_HEADER, "k-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.resultCode").value("IDEMPOTENCY_KEY_REUSED"));
		assertEquals(0, probe.calls.get());
	}

	@Test
	@DisplayName("(5) claim 실패 + PENDING → 409 IDEMPOTENCY_IN_PROGRESS(컨트롤러 미호출)")
	void claimFalsePending_inProgress() throws Exception {
		when(store.claim(anyString(), anyString(), any())).thenReturn(false);
		when(store.find(anyString()))
				.thenReturn(Optional.of(IdempotencyRecord.pending(IdempotencyFilter.sha256Hex(bodyBytes()))));

		mockMvc.perform(post("/houses/insert")
						.header(KEY_HEADER, "k-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.resultCode").value("IDEMPOTENCY_IN_PROGRESS"));
		assertEquals(0, probe.calls.get());
	}

	@Test
	@DisplayName("(6-a) claim 인프라 장애 + 헤더 있음 → 503 IDEMPOTENCY_UNAVAILABLE(fail-closed)")
	void claimInfraFailure_503() throws Exception {
		when(store.claim(anyString(), anyString(), any()))
				.thenThrow(new IdempotencyInfraException("redis down", new RuntimeException()));

		mockMvc.perform(post("/houses/insert")
						.header(KEY_HEADER, "k-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.resultCode").value("IDEMPOTENCY_UNAVAILABLE"));
		assertEquals(0, probe.calls.get());
	}

	@Test
	@DisplayName("(7) 비2xx 응답 → release 호출(promote 미호출)")
	void nonSuccess_releases() throws Exception {
		when(store.claim(anyString(), anyString(), any())).thenReturn(true);

		mockMvc.perform(post("/houses/insert")
						.header(KEY_HEADER, "k-1")
						.header("X-Test-Status", "400")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isBadRequest());
		assertEquals(1, probe.calls.get());
		verify(store).release(anyString());
		verify(store, never()).promote(anyString(), any(), any());
	}

	@Test
	@DisplayName("(8) 스코프 — userSn/channel 이 다르면 키 문자열도 달라짐(정확 조합)")
	void keyScopeIsolation() {
		String base = IdempotencyFilter.buildKey(7L, "PARTNER", "POST", "/houses/insert", "k-1");
		assertEquals("idem:v1:7:PARTNER:POST:/houses/insert:k-1", base);
		assertNotEquals(base, IdempotencyFilter.buildKey(8L, "PARTNER", "POST", "/houses/insert", "k-1"));
		assertNotEquals(base, IdempotencyFilter.buildKey(7L, "OPERATOR", "POST", "/houses/insert", "k-1"));
		assertNotEquals(base, IdempotencyFilter.buildKey(7L, "PARTNER", "POST", "/houses/insert", "k-2"));
	}

	@Test
	@DisplayName("설정 — 비양수 TTL 은 @PostConstruct 검증에서 거부(기동 거부)")
	void properties_rejectNonPositiveTtl() {
		IdempotencyProperties p = new IdempotencyProperties();
		ReflectionTestUtils.setField(p, "pendingTtlSeconds", 0L);
		ReflectionTestUtils.setField(p, "doneTtlSeconds", 86400L);
		ReflectionTestUtils.setField(p, "guardedPathsRaw", new String[] {"/houses/insert"});
		ReflectionTestUtils.setField(p, "replayHeadersRaw", new String[] {"Location"});
		assertThrows(IllegalStateException.class, p::validateConfig);
	}

	@Test
	@DisplayName("(6) guarded-paths 에 필수 경로(/reservations) 누락 → 기동 거부(오버라이드 조용한 누락 차단)")
	void properties_rejectMissingRequiredPath() {
		IdempotencyProperties p = new IdempotencyProperties();
		ReflectionTestUtils.setField(p, "pendingTtlSeconds", 60L);
		ReflectionTestUtils.setField(p, "doneTtlSeconds", 86400L);
		// /reservations 를 뺀 4경로만 = 필수 subset 위반 → 기동 거부되어야 한다.
		ReflectionTestUtils.setField(p, "guardedPathsRaw",
				new String[] {"/houses/insert", "/operator/houses/insert", "/rooms/insert", "/operator/rooms/insert"});
		ReflectionTestUtils.setField(p, "replayHeadersRaw", new String[] {"Location"});
		assertThrows(IllegalStateException.class, p::validateConfig);
	}

	@Test
	@DisplayName("(7) /reservations → MEMBER 채널 키로 분류(격리는 userSn, 라벨만 MEMBER)")
	void reservationsPath_memberChannelKey() throws Exception {
		when(store.claim(anyString(), anyString(), any())).thenReturn(true);

		mockMvc.perform(post("/reservations")
						.header(KEY_HEADER, "rk-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isCreated());

		ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
		verify(store).claim(keyCap.capture(), anyString(), any());
		assertEquals("idem:v1:7:MEMBER:POST:/reservations:rk-1", keyCap.getValue());
	}

	@Test
	@DisplayName("(M1) 캐시된 본문이 컨트롤러까지 원형 전달됨(@RequestBody/getInputStream — opt-in 무음장애 방지)")
	void cachedBody_reachesControllerIntact() throws Exception {
		when(store.claim(anyString(), anyString(), any())).thenReturn(true);

		mockMvc.perform(post("/houses/insert")
						.header(KEY_HEADER, "k-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(BODY))
				.andExpect(status().isCreated());
		// 핵심 증거 — 필터가 요청을 래핑한 뒤에도 컨트롤러가 원본 본문을 그대로 받는다(빈 본문 무음장애 차단).
		assertEquals(BODY, probe.lastBody);
		assertEquals(1, probe.calls.get());
	}

	@Test
	@DisplayName("(M1-b) 비-ASCII 본문도 getReader 경로에서 charset 대로 정확히 디코딩됨")
	void cachedBody_nonAsciiViaReader() throws Exception {
		String korean = "{\"name\":\"한옥 별채\"}";
		when(store.claim(anyString(), anyString(), any())).thenReturn(true);

		mockMvc.perform(post("/rooms/insert")
						.header(KEY_HEADER, "k-2")
						.contentType(MediaType.APPLICATION_JSON)
						.characterEncoding("UTF-8")
						.content(korean.getBytes(StandardCharsets.UTF_8)))
				.andExpect(status().isCreated());
		assertEquals(korean, probe.lastReaderBody);
	}

	@Test
	@DisplayName("(M2-filter) 같은 키 2요청 → 컨트롤러 정확히 1회(두 번째는 409 IN_PROGRESS)")
	void sameKey_controllerCalledOnce() throws Exception {
		// 첫 요청만 선점 성공, 두 번째는 미획득 + PENDING → 409. 컨트롤러는 단 1회만 실행되어야 한다.
		when(store.claim(anyString(), anyString(), any())).thenReturn(true, false);
		when(store.find(anyString()))
				.thenReturn(Optional.of(IdempotencyRecord.pending(IdempotencyFilter.sha256Hex(bodyBytes()))));

		mockMvc.perform(post("/houses/insert").header(KEY_HEADER, "k-1")
						.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/houses/insert").header(KEY_HEADER, "k-1")
						.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.resultCode").value("IDEMPOTENCY_IN_PROGRESS"));
		assertEquals(1, probe.calls.get());
	}

	// ------------------------------------------------------------------
	// 검증용 프로브 컨트롤러 (테스트 전용 — 운영 코드 아님). X-Test-Status 로 응답 상태를 구동.
	// ------------------------------------------------------------------

	@RestController
	static class ProbeController {

		final AtomicInteger calls = new AtomicInteger();
		volatile String lastBody;       // getInputStream/@RequestBody 경로로 컨트롤러가 실제로 받은 본문
		volatile String lastReaderBody; // getReader 경로로 받은 본문(charset 디코딩 검증용)

		// getInputStream 경로(@RequestBody) — 운영 insert 와 동일 경로. 캐시된 본문이 컨트롤러까지 원형 전달되는지 증명.
		@PostMapping({"/houses/insert", "/operator/houses/insert", "/operator/rooms/insert", "/reservations"})
		ResponseEntity<ApiResponse<String>> insert(
				@RequestBody(required = false) String body,
				@RequestHeader(value = "X-Test-Status", defaultValue = "201") int status) {
			calls.incrementAndGet();
			lastBody = body;
			return ResponseEntity.status(HttpStatus.valueOf(status)).body(ApiResponse.ok("created"));
		}

		// getReader 경로 — 래퍼가 charset 대로 정확히 디코딩하는지(비-ASCII) 증명.
		@PostMapping("/rooms/insert")
		ResponseEntity<ApiResponse<String>> insertViaReader(HttpServletRequest request) throws IOException {
			calls.incrementAndGet();
			lastReaderBody = FileCopyUtils.copyToString(request.getReader());
			return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("created"));
		}
	}
}
