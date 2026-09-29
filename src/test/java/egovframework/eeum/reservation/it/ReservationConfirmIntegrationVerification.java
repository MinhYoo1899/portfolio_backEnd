package egovframework.eeum.reservation.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import javax.sql.DataSource;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import egovframework.eeum.base.crypto.FieldEncryptionKeyProvider;
import egovframework.eeum.base.crypto.FieldEncryptor;
import egovframework.eeum.base.crypto.GuestMblTelnoEncryptedTypeHandler;
import egovframework.eeum.base.crypto.PicMblTelnoEncryptedTypeHandler;
import egovframework.eeum.reservation.service.reservation.dao.ReservationMapper;
import egovframework.eeum.reservation.vo.ReservationVO;

/**
 * 예약 확정 매퍼 통합검증(@MybatisTest + H2 MySQL 호환모드) — mock 이 못 잡는 두 회귀를 실 SQL·실 트랜잭션으로 방어.
 *
 * <p>① 부분실패 롤백: 다박 확정 중 한 밤이 매진(SOLD_OUT)이면 이미 증가한 다른 밤의 SOLD_QTY 까지 전부 원복되는지를
 *   실제 트랜잭션 롤백 + 실 매퍼 SQL({@link ReservationMapper#confirmNight})로 검증(부분차감 금지).
 * <p>② 소유권 SQL: 타 소유(userB) 예약을 userA 로 {@link ReservationMapper#selectReservationForUpdate} 하면 null,
 *   본인(userB)이면 non-null — RESERVER_USER_SN 술어가 SQL 레벨에서 살아있는지 검증(삭제 회귀 방어).
 *
 * <p>정책: Testcontainers 미사용(팀 정책) — H2 인메모리 MySQL 모드. 운영 매퍼 SQL 은 UTC_TIMESTAMP(6)/FOR UPDATE 를
 *   쓰므로, UTC_TIMESTAMP 는 {@link H2Compat} 정적 메서드로 ALIAS 보정(reservation-it-schema.sql)하고
 *   FOR UPDATE 는 H2 가 트랜잭션 내 지원한다. GUEST 전화 암호화 TypeHandler(무인자 생성자 없음)는
 *   {@link ItMybatisConfig} 가 인스턴스로 등록(EgovConfigAppMapper 미러)해 매퍼 XML 파싱이 성립한다.
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(ReservationConfirmIntegrationVerification.ItMybatisConfig.class)
class ReservationConfirmIntegrationVerification {

	private static final long ROOM_SN = 9001L;
	private static final long USER_A = 8001L;
	private static final long USER_B = 8002L;
	private static final int QTY = 1;

	private static final LocalDate D1 = LocalDate.of(2026, 8, 10);
	private static final LocalDate D2 = LocalDate.of(2026, 8, 11);
	private static final LocalDate D3 = LocalDate.of(2026, 8, 12);

	@Autowired
	private ReservationMapper reservationMapper;

	@Autowired
	private DataSource dataSource;

	/**
	 * H2 를 MySQL 호환모드로 고정 — @AutoConfigureTestDatabase(replace=NONE) 와 함께 이 URL 을 그대로 사용.
	 * DB_CLOSE_DELAY=-1 로 커넥션풀 전 커넥션이 같은 인메모리 DB(스키마·ALIAS 공유)를 보게 한다.
	 * mapUnderscoreToCamelCase / 매퍼 XML 위치 / DDL 초기화도 여기서 프로퍼티로 주입한다.
	 */
	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url",
				() -> "jdbc:h2:mem:resvit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
		registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
		registry.add("spring.datasource.username", () -> "sa");
		registry.add("spring.datasource.password", () -> "");
		// 운영 마이그레이션(MySQL 전용 DDL — ENGINE/COLLATE)은 H2 에서 파싱 실패하므로 슬라이스에서 비활성.
		// 스키마는 아래 spring.sql.init(H2 호환 DDL)로만 구성한다.
		registry.add("spring.flyway.enabled", () -> "false");
		registry.add("spring.liquibase.enabled", () -> "false");
		// 운영 mapper-config.xml 과 동일: 컬럼(A_COLUMN) → 프로퍼티(aColumn) 자동 매핑.
		registry.add("mybatis.configuration.map-underscore-to-camel-case", () -> "true");
		// 실 매퍼 XML(운영과 동일 파일) 로드 — namespace=FQCN 바인딩.
		registry.add("mybatis.mapper-locations",
				() -> "classpath*:/egovframework/mapper/**/reservation/ReservationMapper_mysql.xml");
		// 스키마(DDL + UTC_TIMESTAMP ALIAS) 를 슬라이스 부팅 시 1회 적용.
		registry.add("spring.sql.init.mode", () -> "always");
		registry.add("spring.sql.init.schema-locations", () -> "classpath:/reservation-it-schema.sql");
	}

	/**
	 * @MybatisTest 슬라이스가 EgovConfigAppMapper(주 컨텍스트 제외)를 안 태우므로, 여기서 최소 wiring 을 미러한다.
	 * (a) FieldEncryptor 를 테스트 키(base64 32바이트)로 구성, (b) 암호화 TypeHandler 인스턴스를 registry 에 등록
	 *   — insertGuest 의 typeHandler=클래스 참조가 무인자 생성 실패 없이 파싱되도록.
	 */
	@TestConfiguration
	static class ItMybatisConfig {

		/** 테스트 전용 필드암호화 키(base64 32바이트) — 실제 GCM 동작(무의미 값이라도 유효 길이). */
		private static final String TEST_KEY_V1_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

		@Bean
		FieldEncryptor fieldEncryptor() {
			return new FieldEncryptor(new FieldEncryptionKeyProvider(TEST_KEY_V1_BASE64));
		}

		@Bean
		ConfigurationCustomizer cryptoTypeHandlerCustomizer(FieldEncryptor fieldEncryptor) {
			return (Configuration configuration) -> {
				configuration.getTypeHandlerRegistry().register(new PicMblTelnoEncryptedTypeHandler(fieldEncryptor));
				configuration.getTypeHandlerRegistry().register(new GuestMblTelnoEncryptedTypeHandler(fieldEncryptor));
			};
		}
	}

	// ======================= Test 1: 부분실패 롤백(실 트랜잭션) =======================

	/**
	 * 다박(D1,D2,D3) 확정 중 D2 가 만석(SOLD=TOTAL)이라 confirmNight=0 → 합계≠3 → RuntimeException 으로 롤백.
	 * D1 은 0→1 로 이미 증가했지만, 롤백 후 세 밤 모두 시드값(0,1,0)으로 원복되어야 한다(부분차감 금지).
	 *
	 * <p>@Transactional(NOT_SUPPORTED) 로 @MybatisTest 의 자동 롤백 트랜잭션을 걷어내고, TransactionTemplate 로
	 *   독립 트랜잭션을 열어 실제 롤백을 관측한다(롤백 후 fresh SELECT 로 원복 확인).
	 */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@Sql(statements = {
			"INSERT INTO ROOM_CALENDAR (ROOM_SN, STAY_DATE, TOTAL_QTY, SOLD_QTY, PRICE_AMT, CLOSED_YN, REG_DT)"
					+ " VALUES (9001, DATE '2026-08-10', 1, 0, 100000, 'N', TIMESTAMP '2026-07-01 00:00:00')",
			"INSERT INTO ROOM_CALENDAR (ROOM_SN, STAY_DATE, TOTAL_QTY, SOLD_QTY, PRICE_AMT, CLOSED_YN, REG_DT)"
					+ " VALUES (9001, DATE '2026-08-11', 1, 1, 100000, 'N', TIMESTAMP '2026-07-01 00:00:00')",
			"INSERT INTO ROOM_CALENDAR (ROOM_SN, STAY_DATE, TOTAL_QTY, SOLD_QTY, PRICE_AMT, CLOSED_YN, REG_DT)"
					+ " VALUES (9001, DATE '2026-08-12', 1, 0, 100000, 'N', TIMESTAMP '2026-07-01 00:00:00')" })
	@DisplayName("Test1: 부분실패 롤백 — D2 매진 시 D1 증가분까지 전부 원복(0,1,0), 부분차감 없음")
	void test1_partialFailureRollsBackAllNights() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		int soldBeforeD1 = soldQty(jdbc, D1);
		int soldBeforeD2 = soldQty(jdbc, D2);
		int soldBeforeD3 = soldQty(jdbc, D3);
		System.out.printf("[Test1] before rollback SOLD_QTY: D1=%d D2=%d D3=%d%n",
				soldBeforeD1, soldBeforeD2, soldBeforeD3);
		assertEquals(0, soldBeforeD1);
		assertEquals(1, soldBeforeD2);
		assertEquals(0, soldBeforeD3);

		PlatformTransactionManager txManager = new DataSourceTransactionManager(dataSource);
		TransactionTemplate tx = new TransactionTemplate(txManager);

		// 실 트랜잭션 안에서 D1,D2,D3 오름차순 confirmNight — 합계≠3 이면 RuntimeException 던져 롤백 유도.
		RuntimeException thrown = assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(status -> {
			int affected = 0;
			affected += reservationMapper.confirmNight(ROOM_SN, D1, QTY, USER_A); // 0→1 (성공)
			affected += reservationMapper.confirmNight(ROOM_SN, D2, QTY, USER_A); // 만석 → 0
			affected += reservationMapper.confirmNight(ROOM_SN, D3, QTY, USER_A); // 0→1 (성공)
			if (affected != 3) {
				// 서비스의 SOLD_OUT → 트랜잭션 롤백 흐름을 모사(RuntimeException 이라 롤백된다).
				throw new IllegalStateException("SOLD_OUT: affected=" + affected + " (expected 3)");
			}
		}));
		System.out.println("[Test1] thrown = " + thrown.getMessage());

		// 롤백 후 fresh SELECT — D1 의 0→1 증가가 원복되어 세 밤 모두 시드값(0,1,0).
		int soldAfterD1 = soldQty(jdbc, D1);
		int soldAfterD2 = soldQty(jdbc, D2);
		int soldAfterD3 = soldQty(jdbc, D3);
		System.out.printf("[Test1] after  rollback SOLD_QTY: D1=%d D2=%d D3=%d%n",
				soldAfterD1, soldAfterD2, soldAfterD3);
		assertEquals(0, soldAfterD1, "D1 증가분(0→1)이 롤백으로 원복되어야 함");
		assertEquals(1, soldAfterD2, "D2 는 애초 미변경(만석)");
		assertEquals(0, soldAfterD3, "D3 미변경(D2 실패로 커밋 안 됨) — 롤백 후 시드값");
	}

	// ======================= Test 2: 소유권 SQL(RESERVER_USER_SN 술어) =======================

	/**
	 * userB 소유 예약을 userA 로 잠금조회하면 null(타 소유 차단), userB 로는 non-null(양성 대조).
	 * RESERVER_USER_SN 술어가 SQL 에서 삭제되면 userA 조회가 non-null 이 되어 이 검증이 깨진다.
	 */
	@Test
	@Sql(statements = "INSERT INTO RESERVATION"
			+ " (ORDER_NO, RESERVATION_STTS_CD, PRTNR_SN, HOUSE_SN, ROOM_SN, RESERVER_USER_SN,"
			+ "  CHECK_IN_DATE, CHECK_OUT_DATE, NGT_CNT, GUEST_CNT, TOTAL_AMT, CRNC_CD, REG_DT, DEL_YN)"
			+ " VALUES ('R-OWN-B', 'HOLD', 7001, 6001, 9001, 8002,"
			+ "  DATE '2026-08-10', DATE '2026-08-12', 2, 1, 200000, 'KRW', TIMESTAMP '2026-07-01 00:00:00', 'N')")
	@DisplayName("Test2: 소유권 SQL — 타 소유(userA)=null, 본인(userB)=non-null(RESERVER_USER_SN 술어 생존)")
	void test2_ownershipPredicateEnforcedAtSql() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		long reservationSn = jdbc.queryForObject(
				"SELECT RESERVATION_SN FROM RESERVATION WHERE ORDER_NO = 'R-OWN-B'", Long.class);

		// 타 소유(userA) — 소유권 WHERE 로 걸러져 null 이어야 한다.
		ReservationVO wrongOwner = reservationMapper.selectReservationForUpdate(reservationSn, USER_A);
		assertNull(wrongOwner, "타 소유(userA) 조회는 null 이어야 함 — RESERVER_USER_SN 술어");

		// 본인(userB) — 양성 대조로 non-null.
		ReservationVO owner = reservationMapper.selectReservationForUpdate(reservationSn, USER_B);
		assertNotNull(owner, "본인(userB) 조회는 non-null 이어야 함(양성 대조)");
		assertEquals(reservationSn, owner.getReservationSn());
		assertEquals(ROOM_SN, owner.getRoomSn());
	}

	// ------------------------------------------------------------------

	/** 특정 숙박일의 SOLD_QTY 를 fresh SELECT(롤백 관측용). */
	private static int soldQty(JdbcTemplate jdbc, LocalDate stayDate) {
		return jdbc.queryForObject(
				"SELECT SOLD_QTY FROM ROOM_CALENDAR WHERE ROOM_SN = ? AND STAY_DATE = ?",
				Integer.class, ROOM_SN, stayDate);
	}
}
