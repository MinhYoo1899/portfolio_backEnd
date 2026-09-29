package egovframework.eeum.base.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * USER_ACNT.MBL_TELNO 암호화 TypeHandler 검증 — set=암호화/get=복호화 round-trip, fieldID 고정(AAD 이식 차단),
 * registry 인스턴스 등록 해석, 일반 String 무오염.
 */
class MblTelnoTypeHandlerVerification {

	private static final String KEY_V1 = Base64.getEncoder().encodeToString(new byte[32]); // 테스트 전용
	private final FieldEncryptor encryptor = new FieldEncryptor(new FieldEncryptionKeyProvider(KEY_V1));
	private final MblTelnoEncryptedTypeHandler handler = new MblTelnoEncryptedTypeHandler(encryptor);

	@Test
	@DisplayName("fieldId = USER_ACNT.MBL_TELNO 고정")
	void fieldIdFrozen() {
		assertEquals("USER_ACNT.MBL_TELNO", MblTelnoEncryptedTypeHandler.FIELD_ID);
	}

	@Test
	@DisplayName("set — 평문을 암호문(v1:)으로 저장")
	void setEncrypts() throws Exception {
		PreparedStatement ps = mock(PreparedStatement.class);
		handler.setParameter(ps, 1, "01012345678", null);
		ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
		verify(ps).setString(eq(1), cap.capture());
		assertTrue(cap.getValue().startsWith("v1:"));
		assertNotEquals("01012345678", cap.getValue());
	}

	@Test
	@DisplayName("get — 암호문을 평문으로 복호화(round-trip)")
	void getDecrypts() throws Exception {
		String ct = encryptor.encrypt("01012345678", MblTelnoEncryptedTypeHandler.FIELD_ID);
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("MBL_TELNO")).thenReturn(ct);
		assertEquals("01012345678", handler.getResult(rs, "MBL_TELNO"));
	}

	@Test
	@DisplayName("다른 fieldId(AAD)로 암호화된 값은 복호화 실패 — 컬럼 간 이식 차단")
	void fieldIdBound() throws Exception {
		String otherCt = encryptor.encrypt("01012345678", "PRTNR_MST.PIC_MBL_TELNO");
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("c")).thenReturn(otherCt);
		assertThrows(FieldCryptoException.class, () -> handler.getResult(rs, "c"));
	}

	@Test
	@DisplayName("registry 인스턴스 등록 — class 참조로 동일 인스턴스 반환, 일반 String 무오염")
	void registryResolvesInstanceNoPollution() {
		TypeHandlerRegistry registry = new Configuration().getTypeHandlerRegistry();
		registry.register(handler);
		TypeHandler<?> resolved = registry.getMappingTypeHandler(MblTelnoEncryptedTypeHandler.class);
		assertSame(handler, resolved);
		assertNotEquals(handler, registry.getTypeHandler(String.class));
	}
}
