package egovframework.eeum.notification;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ② 알림설정 계약(파일 검사) — V53 감사5컬럼·CHECK, 매퍼의 운영자 경계·감사컬럼·DEL_YN 필터 SQL 확인.
 * (XML/DDL 은 컴파일 검증 사각지대라 파일 문자열로 회귀 고정 — MapperXmlReferenceVerification 과 동일 취지)
 */
class OperatorNotiSettingContractVerification {

	private static final Path BASE = Path.of(System.getProperty("basedir", "."));

	private static String norm(Path p) throws IOException {
		return Files.readString(p).replaceAll("\\s+", " ");
	}

	@Test
	@DisplayName("V53 — 감사5컬럼(REG_DT/MDFCN_DT/RGTR_SN/MDFR_SN/DEL_YN) + DEL_YN CHECK")
	void v53HasAuditColumns() throws IOException {
		String ddl = norm(BASE.resolve("src/main/resources/db/migration/V53__operator_noti_setting.sql"));
		assertTrue(ddl.contains("REG_DT DATETIME(6) NOT NULL"), "REG_DT NOT NULL");
		assertTrue(ddl.contains("MDFCN_DT DATETIME(6) NOT NULL"), "MDFCN_DT NOT NULL");
		assertTrue(ddl.contains("RGTR_SN BIGINT NOT NULL"), "RGTR_SN NOT NULL");
		assertTrue(ddl.contains("MDFR_SN BIGINT NULL"), "MDFR_SN NULL");
		assertTrue(ddl.contains("DEL_YN CHAR(1) NOT NULL DEFAULT 'N'"), "DEL_YN NOT NULL DEFAULT 'N'");
		assertTrue(ddl.contains("CHECK (DEL_YN IN ('Y','N'))"), "DEL_YN CHECK Y/N");
	}

	@Test
	@DisplayName("매퍼 — 운영자 경계(OPERATOR+DEL_YN), 조회 DEL_YN 필터, upsert 감사컬럼(RGTR_SN/MDFR_SN)")
	void mapperEnforcesOperatorBoundaryAndAudit() throws IOException {
		String xml = norm(BASE.resolve(
				"src/main/resources/egovframework/mapper/eeum/notification/OperatorNotiSettingMapper_mysql.xml"));
		// 운영자 경계 검증 SQL
		assertTrue(xml.contains("USER_TYPE_CD = 'OPERATOR'"), "existsOperator 는 USER_TYPE_CD='OPERATOR' 검증");
		assertTrue(xml.contains("SELECT EXISTS ( SELECT 1 FROM USER_ACNT WHERE USER_SN = #{userSn} AND USER_TYPE_CD = 'OPERATOR' AND DEL_YN = 'N')"),
				"existsOperator 계약(OPERATOR + 미삭제)");
		// 조회는 미삭제만
		assertTrue(xml.contains("FROM OPERATOR_NOTI_SETTING WHERE USER_SN = #{userSn} AND DEL_YN = 'N'"),
				"selectByUserSn 은 DEL_YN='N' 필터");
		// upsert 감사컬럼: INSERT RGTR_SN, UPDATE MDFR_SN
		assertTrue(xml.contains("RGTR_SN, DEL_YN"), "insert 컬럼에 RGTR_SN·DEL_YN");
		assertTrue(xml.contains("MDFR_SN = #{userSn}"), "update 에 MDFR_SN=본인");
	}
}
