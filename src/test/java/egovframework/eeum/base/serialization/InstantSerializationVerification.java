package egovframework.eeum.base.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.eeum.account.web.dto.AccountDetailResponse;
import egovframework.eeum.account.vo.AccountVO;
import egovframework.eeum.inquiry.vo.InqrInfoVO;
import egovframework.eeum.inquiry.web.dto.InqrDetailResponse;

/**
 * 응답 DTO 발생시각 직렬화 검증 — 발생 순간은 ISO-8601 UTC `Z`(오프셋 포함)로 나가야 한다 (타임존 표준 §2).
 *
 * <p>{@code @JsonTest} 로 Spring Boot 가 {@code application.properties}(특히
 * {@code spring.jackson.serialization.write-dates-as-timestamps=false})를 반영해 구성한 <b>실제 ObjectMapper 빈</b>을
 * 주입받아 검증한다 — ObjectMapper 를 수동 생성/설정하지 않으므로, 운영 Jackson 설정이 빠지면(타임스탬프 출력으로 회귀하면)
 * 이 테스트가 깨진다(가짜 초록 방지).
 */
@JsonTest
class InstantSerializationVerification {

	/** 오프셋(Z) 포함 ISO-8601 instant 패턴 — 끝이 Z 로 닫혀야 한다. */
	private static final Pattern ISO_INSTANT_Z =
			Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");

	/** Spring Boot 가 application.properties 를 반영해 만든 실제 빈 — 수동 구성 금지(실제 운영 설정 검증). */
	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("계정 상세 — regDt 가 ISO-8601 UTC Z 문자열로 직렬화된다")
	void accountDetailRegDtSerializesAsIsoZ() throws Exception {
		LocalDateTime stored = LocalDateTime.of(2026, 6, 18, 5, 50, 49, 692_060_000);
		AccountVO vo = new AccountVO();
		vo.setUserSn(1024L);
		vo.setRegDt(stored);

		AccountDetailResponse response = AccountDetailResponse.from(vo);

		JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(response));
		String regDt = node.get("regDt").asText();

		assertTrue(node.get("regDt").isTextual(), "regDt 는 숫자 타임스탬프가 아닌 문자열이어야 한다");
		assertTrue(ISO_INSTANT_Z.matcher(regDt).matches(),
				"regDt 는 ISO-8601 UTC Z 형식이어야 한다: " + regDt);
		// 값 보존(표현만 Z 추가) — 저장 LocalDateTime(UTC) == 직렬화된 instant
		assertEquals(stored.toInstant(ZoneOffset.UTC), Instant.parse(regDt));
	}

	@Test
	@DisplayName("문의 상세 — regDt 가 ISO-8601 UTC Z 문자열로 직렬화된다")
	void inquiryDetailRegDtSerializesAsIsoZ() throws Exception {
		LocalDateTime stored = LocalDateTime.of(2026, 6, 18, 5, 50, 49, 692_060_000);
		InqrInfoVO vo = new InqrInfoVO();
		vo.setInqrSn(7L);
		vo.setRegDt(stored);

		InqrDetailResponse response = InqrDetailResponse.from(vo, List.of());

		JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(response));
		String regDt = node.get("regDt").asText();

		assertTrue(node.get("regDt").isTextual(), "regDt 는 숫자 타임스탬프가 아닌 문자열이어야 한다");
		assertTrue(ISO_INSTANT_Z.matcher(regDt).matches(),
				"regDt 는 ISO-8601 UTC Z 형식이어야 한다: " + regDt);
		assertEquals(stored.toInstant(ZoneOffset.UTC), Instant.parse(regDt));
	}

	@Test
	@DisplayName("발생시각이 null 이면 직렬화에서도 null 로 유지된다")
	void nullInstantStaysNull() throws Exception {
		AccountVO vo = new AccountVO();
		vo.setUserSn(1024L);
		vo.setRegDt(null);

		AccountDetailResponse response = AccountDetailResponse.from(vo);

		JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(response));
		assertTrue(node.get("regDt").isNull(), "regDt 가 null 이면 직렬화도 null 이어야 한다");
	}
}
