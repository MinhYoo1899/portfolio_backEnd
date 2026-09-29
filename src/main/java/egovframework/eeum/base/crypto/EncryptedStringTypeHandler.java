package egovframework.eeum.base.crypto;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * 암호화 문자열 컬럼용 MyBatis TypeHandler 베이스 (crypto-standard.md §2).
 *
 * <p>저장 시 {@link FieldEncryptor#encrypt}, 조회 시 {@link FieldEncryptor#decrypt} 를 거친다.
 * {@code fieldId}(AAD)는 서브클래스가 생성자로 고정(동결)한다. null/빈문자열 처리는 FieldEncryptor 정책에 위임.
 * 복호화 실패는 {@link FieldCryptoException} 으로 전파(평문 미노출).
 *
 * <p><b>중요 — 기본 String 핸들러 오염 방지:</b> {@code BaseTypeHandler<String>} 를 상속하면 그 타입이
 * {@code TypeReference<String>} 이므로 {@code setTypeHandlers(instance)} 등록 시 <b>javaType=String 의 기본 핸들러로
 * 잡혀 모든 String 컬럼이 암호화</b>되는 치명 문제가 있다. 그래서 {@link TypeHandler}{@code <String>} 를
 * <b>직접 구현</b>한다 — registry 등록이 클래스 조회 맵(allTypeHandlersMap)만 채우고 javaType 기본 매핑은
 * 건드리지 않으므로, XML 에서 {@code typeHandler=클래스} 로 <b>명시 지정한 컬럼/파라미터에만</b> 적용된다
 * (등록은 {@code EgovConfigAppMapper.sqlSession()}, {@code @MappedTypes} 미부착).
 */
public abstract class EncryptedStringTypeHandler implements TypeHandler<String> {

	private final FieldEncryptor encryptor;
	private final String fieldId;

	protected EncryptedStringTypeHandler(FieldEncryptor encryptor, String fieldId) {
		this.encryptor = encryptor;
		this.fieldId = fieldId;
	}

	@Override
	public void setParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
		// null 은 보수적으로 setNull(VARCHAR) — 일부 JDBC 드라이버/경로에서 setString(i, null) 의 타입추론 모호성 방지.
		if (parameter == null) {
			ps.setNull(i, Types.VARCHAR);
			return;
		}
		// "" 는 FieldEncryptor 정책상 그대로 "" 통과(암호화 안 함). 그 외는 v{ver}:Base64(...) 암호문.
		ps.setString(i, encryptor.encrypt(parameter, fieldId));
	}

	@Override
	public String getResult(ResultSet rs, String columnName) throws SQLException {
		return encryptor.decrypt(rs.getString(columnName), fieldId);
	}

	@Override
	public String getResult(ResultSet rs, int columnIndex) throws SQLException {
		return encryptor.decrypt(rs.getString(columnIndex), fieldId);
	}

	@Override
	public String getResult(CallableStatement cs, int columnIndex) throws SQLException {
		return encryptor.decrypt(cs.getString(columnIndex), fieldId);
	}
}
