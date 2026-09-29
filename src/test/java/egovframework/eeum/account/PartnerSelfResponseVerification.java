package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.account.vo.PartnerVO;
import egovframework.eeum.account.web.dto.PartnerSelfResponse;

/**
 * PartnerSelfResponse(파트너 본인 DTO) — 운영자 내부필드 미혼입 + picMblTelno 원문 미노출(마스킹) 회귀.
 * (codex 권고 ② 반영 — 금지필드를 record component/직렬화로 못박는다.)
 */
class PartnerSelfResponseVerification {

	@Test
	@DisplayName("record component 에 운영자 내부필드(aprvUserSn/aprvDt)가 없다")
	void hasNoOperatorInternalFields() {
		Set<String> components = Arrays.stream(PartnerSelfResponse.class.getRecordComponents())
				.map(RecordComponent::getName).collect(Collectors.toSet());
		assertFalse(components.contains("aprvUserSn"), "aprvUserSn 노출 금지");
		assertFalse(components.contains("aprvDt"), "aprvDt 노출 금지");
		// 본인에게 보여줘야 하는 핵심 필드는 존재
		assertTrue(components.contains("picMblTelno") && components.contains("aprvStts")
				&& components.contains("mailOrderSalesNo"));
	}

	@Test
	@DisplayName("직렬화 결과 — picMblTelno 원문 미노출(마스킹만), 금지필드 키 부재")
	void serializationMasksAndHidesForbiddenFields() throws Exception {
		PartnerVO vo = new PartnerVO();
		vo.setPrtnrSn(7L);
		vo.setUserSn(100L);
		vo.setConm("고즈넉한옥");
		vo.setPicMblTelno("01012345678"); // 복호화된 평문(매퍼 계층) — 응답엔 마스킹만 나가야 함
		// regDt 미설정(null) → JavaTimeModule 없이도 직렬화 가능

		String json = new ObjectMapper().writeValueAsString(PartnerSelfResponse.from(vo));

		assertFalse(json.contains("01012345678"), "picMblTelno 원문이 직렬화 결과에 노출되면 안 됨");
		assertTrue(json.contains("010-****-5678"), "마스킹값만 노출되어야 함");
		assertFalse(json.contains("aprvUserSn"), "aprvUserSn 키 부재");
		assertFalse(json.contains("aprvDt"), "aprvDt 키 부재");
		assertEquals("100", PartnerSelfResponse.from(vo).userSn());
	}
}
