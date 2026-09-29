package egovframework.eeum.base.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Base64;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * picMblTelno 암호화 TypeHandler(Phase B) 검증 — set=암호화/get=복호화 round-trip, fieldId 고정,
 * null/"" 위임, 그리고 <b>registry 인스턴스 등록이 XML class 참조로 해석되는지 + 일반 String 컬럼 무오염</b>.
 */
class PicMblTelnoTypeHandlerVerification {

	private static final String KEY_V1 = Base64.getEncoder().encodeToString(new byte[32]); // 테스트 전용
	private final FieldEncryptor encryptor = new FieldEncryptor(new FieldEncryptionKeyProvider(KEY_V1));
	private final PicMblTelnoEncryptedTypeHandler handler = new PicMblTelnoEncryptedTypeHandler(encryptor);

	@Test
	@DisplayName("set 파라미터 — 평문을 암호문(v1:)으로 저장")
	void setEncrypts() throws Exception {
		PreparedStatement ps = mock(PreparedStatement.class);
		handler.setParameter(ps, 1, "01012345678", null);
		ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
		verify(ps).setString(eq(1), cap.capture());
		assertTrue(cap.getValue().startsWith("v1:"));
		assertNotEquals("01012345678", cap.getValue());
	}

	@Test
	@DisplayName("get 결과 — 암호문을 평문으로 복호화(round-trip)")
	void getDecrypts() throws Exception {
		String ct = encryptor.encrypt("01012345678", PicMblTelnoEncryptedTypeHandler.FIELD_ID);
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("PIC_MBL_TELNO")).thenReturn(ct);
		assertEquals("01012345678", handler.getResult(rs, "PIC_MBL_TELNO"));
	}

	@Test
	@DisplayName("null/빈문자열 — FieldEncryptor 정책 위임(통과, 암호화 안 함)")
	void nullAndBlankDelegated() throws Exception {
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("c")).thenReturn(null);
		assertNull(handler.getResult(rs, "c"));
		when(rs.getString("c")).thenReturn("");
		assertEquals("", handler.getResult(rs, "c"));

		PreparedStatement ps = mock(PreparedStatement.class);
		handler.setParameter(ps, 1, "", null);
		verify(ps).setString(1, ""); // "" 는 그대로(암호화 안 함)
	}

	@Test
	@DisplayName("fieldId 고정(PRTNR_MST.PIC_MBL_TELNO) — 다른 fieldId(AAD)로 암호화된 값은 복호화 실패")
	void fieldIdBound() throws Exception {
		assertEquals("PRTNR_MST.PIC_MBL_TELNO", PicMblTelnoEncryptedTypeHandler.FIELD_ID);
		String otherCt = encryptor.encrypt("01012345678", "USER_ACNT.MBL_TELNO");
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString("c")).thenReturn(otherCt);
		assertThrows(FieldCryptoException.class, () -> handler.getResult(rs, "c"));
	}

	@Test
	@DisplayName("등록 검증 — registry.register(instance) 후 class 참조로 동일 인스턴스 반환(XML typeHandler=class 해석 경로)")
	void registryResolvesRegisteredInstance() {
		TypeHandlerRegistry registry = new Configuration().getTypeHandlerRegistry();
		registry.register(handler); // SqlSessionFactoryBean#setTypeHandlers 가 항목별 수행하는 등록
		TypeHandler<?> resolved = registry.getMappingTypeHandler(PicMblTelnoEncryptedTypeHandler.class);
		assertSame(handler, resolved); // MyBatis 가 새로 만들지 않고 주입된 인스턴스를 반환
	}

	@Test
	@DisplayName("일반 String 컬럼 무오염 — 기본 String 핸들러는 우리 핸들러가 아님(@MappedTypes 미부착)")
	void doesNotPolluteDefaultStringHandler() {
		TypeHandlerRegistry registry = new Configuration().getTypeHandlerRegistry();
		registry.register(handler);
		assertNotSame(handler, registry.getTypeHandler(String.class));
	}

	@Test
	@DisplayName("set 파라미터 null — setString(null) 대신 보수적으로 setNull(VARCHAR) 호출")
	void setParameterNullUsesSetNullVarchar() throws Exception {
		PreparedStatement ps = mock(PreparedStatement.class);
		handler.setParameter(ps, 3, null, null);
		verify(ps).setNull(3, Types.VARCHAR);
	}

	@Test
	@DisplayName("get 결과(ResultSet, columnIndex) — 인덱스 경로도 복호화(round-trip)")
	void getResultByColumnIndexDecrypts() throws Exception {
		String ct = encryptor.encrypt("01012345678", PicMblTelnoEncryptedTypeHandler.FIELD_ID);
		ResultSet rs = mock(ResultSet.class);
		when(rs.getString(2)).thenReturn(ct);
		assertEquals("01012345678", handler.getResult(rs, 2));
	}

	@Test
	@DisplayName("get 결과(CallableStatement, columnIndex) — CallableStatement 경로도 복호화(round-trip)")
	void getResultFromCallableStatementDecrypts() throws Exception {
		String ct = encryptor.encrypt("01012345678", PicMblTelnoEncryptedTypeHandler.FIELD_ID);
		CallableStatement cs = mock(CallableStatement.class);
		when(cs.getString(2)).thenReturn(ct);
		assertEquals("01012345678", handler.getResult(cs, 2));
	}

	@Test
	@DisplayName("VARCHAR 경로 무오염 — (String, JdbcType.VARCHAR) 조회도 우리 핸들러가 아님")
	void doesNotPolluteStringVarcharPath() {
		TypeHandlerRegistry registry = new Configuration().getTypeHandlerRegistry();
		registry.register(handler);
		assertNotSame(handler, registry.getTypeHandler(String.class, JdbcType.VARCHAR));
	}
}
