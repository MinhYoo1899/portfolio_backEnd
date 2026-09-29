package egovframework.eeum.notification;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ③a 알림함 계약(파일 검사) — V54 감사5컬럼·필수 CHECK·UNIQUE(RCVR,TYPE,EVENT_KEY)·인덱스, 매퍼의 운영자경계·90일·본인읽음 SQL.
 */
class OperatorNotificationContractVerification {

	private static final Path BASE = Path.of(System.getProperty("basedir", "."));

	private static String norm(String rel) throws IOException {
		return Files.readString(BASE.resolve(rel)).replaceAll("\\s+", " ");
	}

	@Test
	@DisplayName("V54 — 감사5컬럼·READ_YN/DEL_YN/타입 CHECK·UNIQUE(RCVR,TYPE,EVENT_KEY)·인덱스")
	void v54Contract() throws IOException {
		String ddl = norm("src/main/resources/db/migration/V54__operator_notification.sql");
		assertTrue(ddl.contains("REG_DT DATETIME(6) NOT NULL"), "REG_DT");
		assertTrue(ddl.contains("MDFCN_DT DATETIME(6) NOT NULL"), "MDFCN_DT");
		assertTrue(ddl.contains("RGTR_SN BIGINT NOT NULL"), "RGTR_SN NOT NULL");
		assertTrue(ddl.contains("MDFR_SN BIGINT NULL"), "MDFR_SN NULL");
		assertTrue(ddl.contains("DEL_YN CHAR(1) NOT NULL DEFAULT 'N'"), "DEL_YN");
		assertTrue(ddl.contains("CHECK (DEL_YN IN ('Y','N'))"), "DEL_YN CHECK");
		assertTrue(ddl.contains("CHECK (READ_YN IN ('Y','N'))"), "READ_YN CHECK");
		assertTrue(ddl.contains("CHECK (NOTI_TYPE_CD IN ('PARTNER_AUTH','PRODUCT_REVIEW','RESERVATION','INQUIRY','SETTLEMENT','COUPON','NOTICE','DM'))"),
				"타입 8종 CHECK");
		// 멱등키 = (수신자, 유형, 이벤트키) — 서로 다른 type 의 동일 eventKey 는 충돌하지 않음
		assertTrue(ddl.contains("UNIQUE KEY UK_OPERATOR_NOTI_EVENT (RCVR_USER_SN, NOTI_TYPE_CD, EVENT_KEY)"),
				"멱등 UNIQUE = (수신자, 유형, eventKey)");
		assertTrue(ddl.contains("KEY IX_OPERATOR_NOTI_RCVR (RCVR_USER_SN, READ_YN, REG_DT)"), "수신자 인덱스");
		// 대상유형/식별자/링크는 NOT NULL(유형이 소유·항상 산출)
		assertTrue(ddl.contains("TRGT_TYPE_CD VARCHAR(30) NOT NULL"), "TRGT_TYPE_CD NOT NULL");
		assertTrue(ddl.contains("TRGT_ID VARCHAR(64) NOT NULL"), "TRGT_ID NOT NULL");
		assertTrue(ddl.contains("LINK_URL VARCHAR(300) NOT NULL"), "LINK_URL NOT NULL");
	}

	@Test
	@DisplayName("매퍼 — 운영자 경계·90일/미삭제 창·본인읽음·멱등 insert")
	void mapperContract() throws IOException {
		String xml = norm("src/main/resources/egovframework/mapper/eeum/notification/OperatorNotificationMapper_mysql.xml");
		assertTrue(xml.contains("USER_TYPE_CD = 'OPERATOR'"), "existsOperator 운영자 경계");
		// 적재 대상은 권한(AUTH_CD) 보유자만 — ALWAYS 유형도 권한 요구
		assertTrue(xml.contains("A.AUTH_CD = #{requiredAuthority}"), "적재 대상 권한 필터");
		assertTrue(xml.contains("REG_DT >= UTC_TIMESTAMP(6) - INTERVAL 90 DAY"), "90일 창");
		assertTrue(xml.contains("RCVR_USER_SN = #{userSn} AND DEL_YN = 'N'"), "본인·미삭제 조회");
		// 개별 읽음은 본인 것만(RCVR_USER_SN 소유권 + 미읽음)
		assertTrue(xml.contains("WHERE OPERATOR_NOTI_SN = #{notiSn} AND RCVR_USER_SN = #{userSn} AND READ_YN = 'N' AND DEL_YN = 'N'"),
				"개별읽음 본인 소유권");
		// eventKey 멱등
		assertTrue(xml.contains("ON DUPLICATE KEY UPDATE OPERATOR_NOTI_SN = OPERATOR_NOTI_SN"), "insert 멱등(중복방지)");
	}
}
