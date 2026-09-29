package egovframework.eeum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 매퍼 XML 회귀 검증 — 패키지 구조 개편(controller→web, 2026-06-12) 이후
 * XML 의 parameterType/resultType 에 옛 패키지 경로가 되살아나는 것을 차단 (codex 권고).
 * (XML 은 컴파일 검증이 없어 ArchUnit 이 못 잡는 사각지대 — 파일 검사로 보완)
 */
class MapperXmlReferenceVerification {

	/** surefire 가 주입하는 basedir 시스템 프로퍼티 우선 — 상위 디렉토리에서 mvn -f 로 실행해도 안전 (codex 권고) */
	private static final Path MAPPER_ROOT = Path.of(System.getProperty("basedir", "."),
			"src", "main", "resources", "egovframework", "mapper", "eeum").normalize();

	@Test
	@DisplayName("매퍼 XML에 폐기된 controller 패키지 경로가 없어야 한다")
	void mapperXml_mustNotReferenceRetiredControllerPackage() throws IOException {
		try (Stream<Path> files = Files.walk(MAPPER_ROOT)) {
			List<Path> offenders = files
					.filter(p -> p.toString().endsWith(".xml"))
					.filter(p -> {
						try {
							return Files.readString(p).contains(".controller.");
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					})
					.toList();
			assertTrue(offenders.isEmpty(), "옛 controller 패키지를 참조하는 매퍼 XML: " + offenders);
		}
	}

	@Test
	@DisplayName("객실/고택 편의시설 표출 조회는 저장가능 scope만 내려야 한다(round-trip 404 방지 — codex 치명 회귀)")
	void amenityDisplayQueries_mustFilterByScope() throws IOException {
		assertScopeFilter("house/RoomAmenityMapper_mysql.xml", "selectActiveAmenitiesByRoom", "APPLY_SCOPE_CD IN ('ROOM','BOTH')");
		assertScopeFilter("house/HouseAmenityMapper_mysql.xml", "selectActiveAmenitiesByHouse", "APPLY_SCOPE_CD IN ('HOUSE','BOTH')");
	}

	/** 지정 select 블록(id ~ {@code </select>})이 기대 scope 필터 문자열을 포함하는지(공백 정규화 후) 단언. */
	private static void assertScopeFilter(String relPath, String selectId, String expected) throws IOException {
		String xml = Files.readString(MAPPER_ROOT.resolve(relPath));
		int start = xml.indexOf("id=\"" + selectId + "\"");
		assertTrue(start >= 0, selectId + " 쿼리를 찾을 수 없음: " + relPath);
		int end = xml.indexOf("</select>", start);
		String block = (end < 0 ? xml.substring(start) : xml.substring(start, end)).replaceAll("\\s+", " ");
		assertTrue(block.contains(expected),
				selectId + " 에 scope 필터(" + expected + ")가 없음 — 조회/저장 scope 정합 회귀(round-trip 404): " + relPath);
	}

	@Test
	@DisplayName("picMblTelno 암호화 매퍼 계약 — 목록/공용·검색절 제외, detail resultMap, insert·update typeHandler 명시")
	void picMblTelnoEncryption_mapperContract() throws IOException {
		// 주석은 제거하고 실제 SQL 만 검사(주석은 설명용으로 PIC_MBL_TELNO 를 언급할 수 있음)
		String code = Files.readString(MAPPER_ROOT.resolve("account/PartnerMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "");
		String th = "PicMblTelnoEncryptedTypeHandler";

		String partnerColumns = between(code, "<sql id=\"partnerColumns\">", "</sql>");
		String listJoinWhere = between(code, "<sql id=\"listJoinWhere\">", "</sql>");
		String resultMap = between(code, "<resultMap id=\"partnerDetailMap\"", "</resultMap>");
		String detail = between(code, "id=\"selectPartnerByPrtnrSn\"", "</select>");
		String insert = between(code, "id=\"insertPartner\"", "</insert>");
		String update = between(code, "id=\"updatePartnerInfo\"", "</update>");

		// 목록 공용 컬럼 + 목록 검색절(WHERE/JOIN)에 PIC_MBL_TELNO 가 섞이면 안 된다(대량열람 평문 복호화/노출·인덱스 누출 차단)
		assertFalse(partnerColumns.contains("PIC_MBL_TELNO"), "partnerColumns(목록·detail 공용)에 PIC_MBL_TELNO 금지");
		assertFalse(listJoinWhere.contains("PIC_MBL_TELNO"), "목록 검색절(listJoinWhere=WHERE/JOIN)에 PIC_MBL_TELNO 금지");

		// detail 만 resultMap 으로 복호화 조회
		assertTrue(detail.contains("resultMap=\"partnerDetailMap\""), "detail 은 resultMap 사용(resultType 금지)");
		assertTrue(detail.contains("PIC_MBL_TELNO"), "detail 은 PIC_MBL_TELNO 를 조회한다");
		assertTrue(resultMap.contains("PIC_MBL_TELNO") && resultMap.contains(th),
				"detail resultMap 이 PIC_MBL_TELNO 를 암호화 TypeHandler 로 매핑");

		// insert/update 는 파라미터에 typeHandler 명시(평문 저장 경로 차단)
		assertTrue(insert.contains("PIC_MBL_TELNO") && insert.contains(th), "insert 값에 typeHandler 명시");
		assertTrue(update.contains("PIC_MBL_TELNO") && update.contains(th), "update SET 값에 typeHandler 명시");

		// PIC_MBL_TELNO 는 위 4개 허용 블록(resultMap/detail/insert/update) 밖에는 절대 등장 금지
		//  → 다른 쿼리의 WHERE/JOIN/ORDER BY/LIKE 등 검색·정렬·조인 절 사용을 구조적으로 차단(암호화 컬럼, crypto-standard §4)
		String stripped = code.replace(resultMap, "").replace(detail, "")
				.replace(insert, "").replace(update, "");
		assertFalse(stripped.contains("PIC_MBL_TELNO"),
				"PIC_MBL_TELNO 가 detail resultMap/select·insert·update 외(목록·검색·정렬·조인 절 등)에 등장 — 암호화 컬럼 오용");
	}

	@Test
	@DisplayName("USER_ACNT.MBL_TELNO 암호화 매퍼 계약 — 검색절 제외, resultMap/insert/update typeHandler + 해시 동시기록")
	void mblTelnoEncryption_mapperContract() throws IOException {
		String th = "MblTelnoEncryptedTypeHandler";

		// (1) OperatorMapper — MBL_TELNO 는 SELECT 목록/resultMap/폰update 에서만. 검색절(WHERE/JOIN) 금지.
		String op = Files.readString(MAPPER_ROOT.resolve("account/OperatorMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "");
		String columns = between(op, "<sql id=\"operatorColumns\">", "</sql>");
		String resultMap = between(op, "<resultMap id=\"operatorResult\"", "</resultMap>");
		String phoneUpdate = between(op, "id=\"updateOperatorPhone\"", "</update>");
		String listWhere = between(op, "<sql id=\"listJoinWhere\">", "</sql>");

		assertTrue(columns.contains("U.MBL_TELNO"), "operatorColumns 는 SELECT 목록으로 MBL_TELNO 조회");
		assertFalse(listWhere.contains("MBL_TELNO"), "목록 검색절(WHERE/JOIN)에 MBL_TELNO 금지");
		assertTrue(resultMap.contains("MBL_TELNO") && resultMap.contains(th), "resultMap 이 MBL_TELNO 를 TypeHandler 로 복호화");
		assertTrue(phoneUpdate.contains(th), "폰 update 는 파라미터에 typeHandler 명시(평문 저장 차단)");
		assertTrue(phoneUpdate.contains("MBL_TELNO_HASH"), "폰 update 는 암호문·검색해시를 같은 UPDATE 로 함께 기록");

		String stripped = op.replace(columns, "").replace(resultMap, "").replace(phoneUpdate, "");
		assertFalse(stripped.contains("MBL_TELNO"),
				"MBL_TELNO 가 컬럼목록/resultMap/폰update 외(WHERE/JOIN/ORDER/LIKE 등)에 등장 — 암호화 컬럼 오용");

		// (2) AccountMapper.insertAccount — 운영자 생성 시 암호문(typeHandler)+검색해시 함께 저장(회원가입은 둘 다 null).
		String acct = Files.readString(MAPPER_ROOT.resolve("account/AccountMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "");
		String insertAccount = between(acct, "id=\"insertAccount\"", "</insert>");
		assertTrue(insertAccount.contains(th), "insertAccount 는 MBL_TELNO 파라미터에 typeHandler 명시");
		assertTrue(insertAccount.contains("MBL_TELNO_HASH"), "insertAccount 는 검색해시 컬럼도 함께 기록");
	}

	@Test
	@DisplayName("캘린더 마감/해제 쿼리 계약 — CLOSED_YN만 변경(가격/수량 보존) + 기존 set(upsert) 무변경")
	void roomCalendarClose_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("house/RoomCalendarMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");

		String close = between(code, "id=\"closeDatesUpsert\"", "</insert>");
		String open = between(code, "id=\"openDates\"", "</update>");
		String set = between(code, "id=\"upsertCalendarRange\"", "</insert>");

		// 마감(Y): placeholder 0/0/'Y' INSERT + ON DUPLICATE 는 CLOSED_YN 만(가격/수량 미덮어쓰기)
		assertTrue(close.contains("0, 0, 'Y'"), "마감 placeholder(TOTAL=0/PRICE=0/CLOSED='Y') 생성");
		assertTrue(close.contains("ON DUPLICATE KEY UPDATE CLOSED_YN = 'Y'"), "기존행은 CLOSED_YN만 'Y'");
		assertFalse(close.contains("TOTAL_QTY = VALUES"), "마감이 TOTAL_QTY 덮어쓰면 안 됨");
		assertFalse(close.contains("PRICE_AMT = VALUES"), "마감이 PRICE_AMT 덮어쓰면 안 됨");

		// 해제(N): CLOSED_YN='N' 만, 가격/수량 미접근
		assertTrue(open.contains("SET CLOSED_YN = 'N'"), "해제는 CLOSED_YN만 'N'");
		assertFalse(open.contains("TOTAL_QTY"), "해제가 TOTAL_QTY 건드리면 안 됨");
		assertFalse(open.contains("PRICE_AMT"), "해제가 PRICE_AMT 건드리면 안 됨");

		// 휴무 사유(CLOSE_RSN) — 마감 시 저장, 해제 시 정리(NULL)
		assertTrue(close.contains("CLOSE_RSN"), "마감이 휴무사유(CLOSE_RSN) 저장");
		assertTrue(open.contains("CLOSE_RSN = NULL"), "해제는 휴무사유(CLOSE_RSN) 정리(NULL)");

		// 기존 set(upsertCalendarRange) 계약 무변경 — 여전히 TOTAL/PRICE 를 VALUES 로 갱신
		assertTrue(set.contains("TOTAL_QTY = VALUES(TOTAL_QTY)") && set.contains("PRICE_AMT = VALUES(PRICE_AMT)"),
				"기존 set(가격·수량 설정) 동작은 유지되어야 함");
	}

	@Test
	@DisplayName("캘린더 가격/수량 쿼리 계약 — upsertPriceStockRange 는 CLOSED_YN=VALUES 미포함(마감 보존), 기존 set 은 유지")
	void roomCalendarPriceStock_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("house/RoomCalendarMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String priceStock = between(code, "id=\"upsertPriceStockRange\"", "</insert>");
		String set = between(code, "id=\"upsertCalendarRange\"", "</insert>");

		// 가격/수량 전용: TOTAL/PRICE 는 갱신, CLOSED_YN 은 ON DUPLICATE 에서 미변경(기존 마감 보존)
		assertTrue(priceStock.contains("TOTAL_QTY = VALUES(TOTAL_QTY)") && priceStock.contains("PRICE_AMT = VALUES(PRICE_AMT)"),
				"가격/수량 upsert 는 TOTAL/PRICE 를 갱신해야 함");
		assertFalse(priceStock.contains("CLOSED_YN = VALUES"),
				"가격/수량 set 이 CLOSED_YN 을 덮으면 마감 보존 계약 위반(요금 변경이 마감일 재오픈)");
		// 기존 set 은 여전히 CLOSED_YN 갱신(동작 무변경)
		assertTrue(set.contains("CLOSED_YN = VALUES(CLOSED_YN)"), "기존 set 의 CLOSED_YN 갱신 동작 유지");
	}

	@Test
	@DisplayName("공개 고택 쿼리 계약 — 가시성 조각(ACTIVE+승인·활성 파트너) 공유, 상세/객실에 비공개·내부컬럼 미노출")
	void publicHouseVisibility_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("house/PublicHouseMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");

		// 가시성 조각 — 목록/상세/존재확인이 같은 기준을 쓴다(노출 기준 분기 금지). keyword 는 별도 조각
		String visible = between(code, "<sql id=\"publicVisibleWhere\">", "</sql>");
		assertTrue(visible.contains("H.HOUSE_STTS_CD = 'ACTIVE'"), "ACTIVE 고택만");
		assertTrue(visible.contains("H.DEL_YN = 'N'"), "삭제 고택 차단");
		assertTrue(visible.contains("P.APRV_STTS = 'APPROVED'"), "미승인 파트너 차단");
		assertTrue(visible.contains("U.ACNT_STTS = 'ACTIVE'"), "정지 파트너 차단");
		assertFalse(visible.contains("keyword"), "가시성 조각은 keyword 파라미터 미참조(파라미터 없는 상세/존재확인에서도 안전)");

		// 상세/존재확인/목록·건수 전부 가시성 조각을 include
		String detail = between(code, "id=\"selectPublicHouseDetail\"", "</select>");
		String exists = between(code, "id=\"countPublicVisibleHouse\"", "</select>");
		String list = between(code, "id=\"selectPublicHouseListOffset\"", "</select>");
		String count = between(code, "id=\"countPublicHouses\"", "</select>");
		for (String block : List.of(detail, exists, list, count)) {
			assertTrue(block.contains("<include refid=\"publicVisibleWhere\"/>"), "가시성 조각 공유 필수");
		}
		assertTrue(list.contains("keywordWhere") && count.contains("keywordWhere"), "목록 계열은 키워드 조각도 include");

		// 상세 SELECT 에 비공개 컬럼(상세주소/우편번호/담당자연락처/감사) 미노출
		assertFalse(detail.contains("DADDR"), "공개 주소는 기본주소(ADDR)까지 — 상세주소 미노출(사용자 결정)");
		assertFalse(detail.contains("H.ZIP"), "우편번호 미노출");
		assertFalse(detail.contains("PIC_MBL_TELNO"), "담당자 연락처(암호화 PII) 미노출");
		assertFalse(detail.contains("REG_DT") || detail.contains("MDFCN_DT"), "감사컬럼 미노출");

		// 공개 객실요약 — ACTIVE 객실만 + 내부 재고설정 미노출
		String rooms = between(code, "id=\"selectPublicRoomsByHouse\"", "</select>");
		assertTrue(rooms.contains("R.ROOM_STTS_CD = 'ACTIVE'"), "ACTIVE 객실만(파트너 관리조회 재사용 금지)");
		assertTrue(rooms.contains("R.DEL_YN = 'N'"), "삭제 객실 차단");
		assertFalse(rooms.contains("DFLT_STOCK_QTY"), "내부 재고설정 미노출");
	}

	@Test
	@DisplayName("가용성 쿼리 계약 — 판매가능 밤 = 마감X + 가격>0 + 재고여유(발행게이트·예약생성과 기준 정렬)")
	void roomAvailabilityPrice_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("house/RoomCalendarMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String avail = between(code, "id=\"selectRoomAvailability\"", "</select>");

		assertTrue(avail.contains("C.CLOSED_YN = 'N'"), "마감일 제외");
		assertTrue(avail.contains("C.PRICE_AMT &gt; 0"), "가격 미설정(0원) 밤은 판매불가 — 공개검색/예약생성/발행게이트 계약 정렬");
		assertTrue(avail.contains("(C.TOTAL_QTY - C.SOLD_QTY) &gt;= #{qty}"), "재고여유 필수");
		assertTrue(avail.contains("H.HOUSE_STTS_CD = 'ACTIVE'") && avail.contains("R.ROOM_STTS_CD = 'ACTIVE'"),
				"ACTIVE 고택·객실만");
		assertTrue(avail.contains("P.APRV_STTS = 'APPROVED'") && avail.contains("U.ACNT_STTS = 'ACTIVE'"),
				"미승인·정지 파트너 차단(06-26 하드닝 유지)");
	}

	@Test
	@DisplayName("운영자 목록 검색 계약 — keyword=고택명 OR 파트너 상호(EXISTS, count 안전)")
	void operatorHouseListKeyword_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("house/HouseMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String where = between(code, "<sql id=\"operatorListWhere\">", "</sql>");

		// keyword = 고택명 OR 파트너 상호(CONM). 상호는 EXISTS 서브쿼리 — PRTNR_MST 미조인 count 에서도 안전
		assertTrue(where.contains("H.HOUSE_NM LIKE"), "고택명 검색 유지");
		assertTrue(where.contains("EXISTS") && where.contains("PK.CONM LIKE"),
				"파트너 상호(CONM) 검색은 EXISTS 서브쿼리로(count 쿼리 안전)");
	}

	@Test
	@DisplayName("drawer 매퍼 계약 — 운영자 검수표면=미삭제 등록객실 전체(ACTIVE 필터 제거·roomSttsCd 유지)·요금·휴관(calendarRoomCount 분모)·연락처 미노출")
	void houseDrawer_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("house/HouseDrawerMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String rooms = between(code, "id=\"selectDrawerRooms\"", "</select>");
		String buckets = between(code, "id=\"selectDrawerPriceBuckets\"", "</select>");
		String closed = between(code, "id=\"selectDrawerHouseClosedDates\"", "</select>");
		String calCount = between(code, "id=\"countDrawerCalendarRooms\"", "</select>");
		String amenities = between(code, "id=\"selectDrawerAmenities\"", "</select>");

		// ★A안: 운영자 drawer = 미삭제 등록 객실 전체(상태 무관). rooms/buckets/closed 에서 ROOM_STTS_CD='ACTIVE' 필터 제거,
		//   단 목록 roomCnt(=ROOM DEL_YN='N' 전체)와 기준 통일. roomSttsCd 는 계속 SELECT 해 응답 상태 구분 유지.
		assertFalse(rooms.contains("R.ROOM_STTS_CD = 'ACTIVE'"), "운영자 drawer 객실은 상태무관(ACTIVE 필터 제거)");
		assertFalse(buckets.contains("R.ROOM_STTS_CD = 'ACTIVE'"), "요일버킷도 상태무관(ACTIVE 필터 제거)");
		assertFalse(closed.contains("R.ROOM_STTS_CD = 'ACTIVE'"), "휴관일도 상태무관(ACTIVE 필터 제거)");
		assertTrue(rooms.contains("R.ROOM_STTS_CD"), "roomSttsCd 는 계속 SELECT(상태 구분 필드 유지)");
		assertTrue(rooms.contains("R.DEL_YN = 'N'") && buckets.contains("R.DEL_YN = 'N'") && closed.contains("R.DEL_YN = 'N'"),
				"삭제 객실은 여전히 제외");

		// 대표요금/요일버킷 = sellable(CLOSED_YN='N' AND PRICE_AMT>0), SOLD_QTY 미참조(매진돼도 요금 유지)
		assertTrue(rooms.contains("C.CLOSED_YN = 'N'") && rooms.contains("C.PRICE_AMT &gt; 0"), "대표요금 sellable 기준");
		assertTrue(buckets.contains("C.CLOSED_YN = 'N'") && buckets.contains("C.PRICE_AMT &gt; 0"), "요일버킷 sellable 기준");
		assertFalse(rooms.contains("SOLD_QTY"), "대표요금에 SOLD_QTY 미참조");
		assertFalse(buckets.contains("SOLD_QTY"), "요일버킷에 SOLD_QTY 미참조");
		assertTrue(buckets.contains("DAYOFWEEK"), "요일 버킷팅");

		// 휴관 = 캘린더 참여 객실 전부가 그 날짜에 CLOSED_YN='Y' + HAVING COUNT(DISTINCT ROOM_SN)=calendarRoomCount(행 누락일 제외)
		assertTrue(closed.contains("C.CLOSED_YN = 'Y'"), "휴관은 CLOSED='Y'");
		assertTrue(closed.contains("COUNT(DISTINCT C.ROOM_SN) = #{calendarRoomCount}"),
				"분모=캘린더 참여 객실 수(계산없는 DRAFT 객실이 기존 휴관일을 삼키지 않도록)");
		// 분모 계산 쿼리 = 창 내 캘린더 행 보유 미삭제 객실 수(상태·CLOSED 무관)
		assertTrue(calCount.contains("COUNT(DISTINCT C.ROOM_SN)") && calCount.contains("R.DEL_YN = 'N'"),
				"휴관 분모=미삭제 캘린더 참여 객실 수");
		assertFalse(calCount.contains("C.CLOSED_YN"), "분모는 CLOSED 여부 무관(참여만 카운트)");

		// 편의시설 표출 정책 정합(RoomAmenityMapper.selectActiveAmenitiesByRoom 과 동일) — 비활성/고택전용 scope 미노출
		assertTrue(amenities.contains("A.USE_YN = 'Y'"), "drawer 편의시설: 카탈로그 활성(USE_YN='Y')만 표출");
		assertTrue(amenities.contains("A.APPLY_SCOPE_CD IN ('ROOM','BOTH')"),
				"drawer 편의시설: 객실 scope('ROOM','BOTH')만(고택전용 제외 — round-trip 회귀 방지)");

		// 담당자 연락처(암호화 PII)는 drawer 어디에도 노출 금지
		assertFalse(code.contains("PIC_MBL_TELNO"), "담당자 연락처(PII) 미노출");

		// ★회귀 고정: 운영자 drawer 확장과 무관하게 public 객실 요약은 계속 ACTIVE-only(공개 노출 정책 불변)
		String publicCode = Files.readString(MAPPER_ROOT.resolve("house/PublicHouseMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String publicRooms = between(publicCode, "id=\"selectPublicRoomsByHouse\"", "</select>");
		assertTrue(publicRooms.contains("R.ROOM_STTS_CD = 'ACTIVE'"), "public 객실요약은 ACTIVE-only 유지(운영자 A안과 분리)");
	}

	@Test
	@DisplayName("고택 지역 저장/필터 계약 — insert/update 에 SIDO/SIGUNGU_REGION_CD, 운영자목록 지역필터, regionUnmappedCount 소스")
	void houseRegion_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("house/HouseMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String insert = between(code, "id=\"insertHouse\"", "</insert>");
		String update = between(code, "id=\"updateHouse\"", "</update>");
		String where = between(code, "<sql id=\"operatorListWhere\">", "</sql>");
		String nullCnt = between(code, "id=\"countHousesWithoutRegion\"", "</select>");

		// insert 가 지역코드를 저장
		assertTrue(insert.contains("SIDO_REGION_CD") && insert.contains("SIGUNGU_REGION_CD"), "insert 에 지역 컬럼 저장");
		// update: 지역 미전송(sidoRegionCd NULL) 시 기존값 보존(CASE) — 백필값 wipe 방지
		assertTrue(update.contains("SIDO_REGION_CD = CASE WHEN #{sidoRegionCd} IS NULL THEN SIDO_REGION_CD"),
				"update 지역 미전송 시 기존 시도 보존");
		assertTrue(update.contains("SIGUNGU_REGION_CD = CASE WHEN #{sidoRegionCd} IS NULL THEN SIGUNGU_REGION_CD"),
				"update 지역 미전송 시 기존 시군구 보존");
		// 운영자 목록 지역 필터(H 컬럼 기준)
		assertTrue(where.contains("H.SIDO_REGION_CD = #{sidoRegionCd}"), "운영자 목록 시도 필터");
		assertTrue(where.contains("H.SIGUNGU_REGION_CD = #{sigunguRegionCd}"), "운영자 목록 시군구 필터");
		// regionUnmappedCount 소스 — 미매핑(시도 NULL) 데이터 카운트(UX 안내용, 기능 플래그와 무관)
		assertTrue(nullCnt.contains("SIDO_REGION_CD IS NULL"), "미매핑 데이터 카운트(regionUnmappedCount 소스)");
	}

	@Test
	@DisplayName("고택 검수 매퍼 계약 — 전이 SQL 소스상태+DEL_YN 가드, 검수이력 insert(반려사유 포함)")
	void houseReview_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("house/HouseMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String transition = between(code, "id=\"transitionHouseReviewStatus\"", "</update>");
		String hstry = between(code, "id=\"insertHouseReviewHstry\"", "</insert>");
		// 전이는 소스상태(fromSttsCd) + 미삭제 가드로 경합/오전이 차단
		assertTrue(transition.contains("HOUSE_STTS_CD = #{fromSttsCd}"), "전이 소스상태 가드");
		assertTrue(transition.contains("DEL_YN = 'N'"), "전이 미삭제 가드");
		assertTrue(transition.contains("HOUSE_STTS_CD = #{toSttsCd}"), "전이 목표상태 SET");
		// 이력은 반려사유(RJCT_RSN)·검수행위(RVW_ACTN_CD) 포함 저장(감사/조회)
		assertTrue(hstry.contains("HOUSE_REVIEW_HSTRY"), "검수이력 테이블");
		assertTrue(hstry.contains("RJCT_RSN"), "반려사유 컬럼 저장");
		assertTrue(hstry.contains("RVW_ACTN_CD"), "검수행위 컬럼 저장");
	}

	@Test
	@DisplayName("콘텐츠 메인배너 매퍼 계약 — 미삭제 가드·결정적 정렬·수정일=등록일·PRODUCT 존재검증")
	void contentBanner_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("content/ContentBannerMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String list = between(code, "id=\"selectBannerList\"", "</select>");
		String detail = between(code, "id=\"selectBannerById\"", "</select>");
		String insert = between(code, "id=\"insertBanner\"", "</insert>");
		String update = between(code, "id=\"updateBanner\"", "</update>");
		String toggle = between(code, "id=\"updateBannerExposeYn\"", "</update>");
		String houseExists = between(code, "id=\"selectActiveHouseExists\"", "</select>");

		// 목록 — 미삭제 + 결정적 정렬(SORT_ORDER ASC, SN DESC)
		assertTrue(list.contains("DEL_YN = 'N'"), "목록 미삭제 가드");
		assertTrue(list.contains("ORDER BY SORT_ORDER ASC, CONTENT_MAIN_BANNER_SN DESC"), "결정적 정렬");
		// 단건/수정/토글 — 미삭제 가드
		assertTrue(detail.contains("DEL_YN = 'N'"), "단건 미삭제 가드");
		assertTrue(update.contains("DEL_YN = 'N'"), "수정 미삭제 가드");
		assertTrue(toggle.contains("DEL_YN = 'N'"), "토글 미삭제 가드");
		// 등록 — REG_DT/MDFCN_DT 둘 다 UTC_TIMESTAMP(6) (수정일=등록일 규칙)
		assertTrue(insert.contains("UTC_TIMESTAMP(6), #{rgtrSn}, UTC_TIMESTAMP(6)"),
				"등록 시 REG_DT=MDFCN_DT=UTC_TIMESTAMP(6)");
		// status 는 저장 컬럼이 아님 — 어떤 쿼리도 저장/조회하지 않는다(서비스 산출)
		assertFalse(code.contains("STTS_CD") || code.contains("STATUS"), "status 는 저장 안 함(서비스 산출)");
		// PRODUCT 존재검증 — 미삭제 고택
		assertTrue(houseExists.contains("HOUSE") && houseExists.contains("DEL_YN = 'N'"),
				"PRODUCT 검증은 미삭제 고택 존재확인");
	}

	@Test
	@DisplayName("콘텐츠 광고배너 매퍼 계약 — 미삭제 가드·노출위치 정렬·수정일=등록일·수정 EXPOSE_YN 보존·soft delete·PRODUCT 존재검증")
	void contentAdBanner_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("content/ContentAdBannerMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String list = between(code, "id=\"selectAdBannerList\"", "</select>");
		String detail = between(code, "id=\"selectAdBannerById\"", "</select>");
		String insert = between(code, "id=\"insertAdBanner\"", "</insert>");
		String update = between(code, "id=\"updateAdBanner\"", "</update>");
		String toggle = between(code, "id=\"updateAdBannerExposeYn\"", "</update>");
		String softDelete = between(code, "id=\"softDeleteAdBanner\"", "</update>");
		String houseExists = between(code, "id=\"selectActiveHouseExists\"", "</select>");

		// 목록 — 미삭제 + 노출위치(AD_BANNER_POSITION) SORT_SEQ 결정적 정렬
		assertTrue(list.contains("DEL_YN = 'N'"), "목록 미삭제 가드");
		assertTrue(list.contains("AD_BANNER_POSITION"), "정렬은 노출위치 공통코드 조인");
		assertTrue(list.contains("ORDER BY C.SORT_SEQ ASC, B.CONTENT_AD_BANNER_SN DESC"), "결정적 정렬(SORT_SEQ, SN DESC)");
		// 단건/수정/토글/삭제 — 미삭제 가드
		assertTrue(detail.contains("DEL_YN = 'N'"), "단건 미삭제 가드");
		assertTrue(update.contains("DEL_YN = 'N'"), "수정 미삭제 가드");
		assertTrue(toggle.contains("DEL_YN = 'N'"), "토글 미삭제 가드");
		assertTrue(softDelete.contains("DEL_YN = 'N'"), "삭제 미삭제 가드");
		// 등록 — REG_DT/MDFCN_DT 둘 다 UTC_TIMESTAMP(6) (수정일=등록일 규칙)
		assertTrue(insert.contains("UTC_TIMESTAMP(6), #{rgtrSn}, UTC_TIMESTAMP(6)"),
				"등록 시 REG_DT=MDFCN_DT=UTC_TIMESTAMP(6)");
		// 수정 — EXPOSE_YN 미변경(보존). 노출 on/off 는 토글 전용
		assertFalse(update.contains("EXPOSE_YN"), "수정은 EXPOSE_YN 보존(SET 절에 넣지 않음)");
		assertTrue(toggle.contains("EXPOSE_YN = #{exposeYn}"), "토글만 EXPOSE_YN 변경");
		// 삭제 — soft delete(DEL_YN='Y')
		assertTrue(softDelete.contains("DEL_YN = 'Y'"), "삭제는 soft delete(DEL_YN='Y')");
		// status 는 저장 컬럼이 아님 — 어떤 쿼리도 저장/조회하지 않는다(서비스 산출)
		assertFalse(code.contains("STTS_CD") || code.contains("STATUS"), "status 는 저장 안 함(서비스 산출)");
		// PRODUCT 존재검증 — 미삭제 고택
		assertTrue(houseExists.contains("HOUSE") && houseExists.contains("DEL_YN = 'N'"),
				"PRODUCT 검증은 미삭제 고택 존재확인");
	}

	@Test
	@DisplayName("고택 일자별요금·정기휴관 매퍼 계약 — 전체 SET + 소유·미삭제 가드, 상세조회에 신규 컬럼 포함")
	void houseDailyRateAndClosure_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("house/HouseMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String rate = between(code, "id=\"updateHouseDailyRate\"", "</update>");
		String closure = between(code, "id=\"updateHouseRegularClosure\"", "</update>");
		String detail = between(code, "id=\"selectHouseDetail\"", "</select>");

		String[] rateCols = {"WEEKDAY_RATE_AMT", "FRIDAY_RATE_AMT", "SATURDAY_RATE_AMT", "PEAK_RATE_AMT"};
		String[] closureCols = {"CLOSED_WKDAY_CD", "HOLIDAY_CLOSED_YN", "LUNAR_HOLIDAY_CLOSED_YN"};

		// 일자별요금 4구간 전체 SET + 소유·미삭제 가드
		for (String col : rateCols) {
			assertTrue(rate.contains(col + " = #{"), col + " SET 필요(전체 교체)");
		}
		assertTrue(rate.contains("PRTNR_SN = #{prtnrSn}") && rate.contains("DEL_YN = 'N'"), "요금 SET 소유·미삭제 가드");
		// 정기휴관 3컬럼 전체 SET + 소유·미삭제 가드
		for (String col : closureCols) {
			assertTrue(closure.contains(col + " = #{"), col + " SET 필요(전체 교체)");
		}
		assertTrue(closure.contains("PRTNR_SN = #{prtnrSn}") && closure.contains("DEL_YN = 'N'"), "휴관 SET 소유·미삭제 가드");
		// 상세 조회가 신규 컬럼 전부 내려준다(PR-06 표시)
		for (String col : rateCols) {
			assertTrue(detail.contains(col), "상세조회에 " + col + " 포함");
		}
		for (String col : closureCols) {
			assertTrue(detail.contains(col), "상세조회에 " + col + " 포함");
		}
	}

	@Test
	@DisplayName("콘텐츠 팝업 매퍼 계약 — 미삭제 가드·필터·수정일=등록일·soft delete·PRODUCT 존재검증·status 미저장")
	void contentPopup_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("content/ContentPopupMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String list = between(code, "id=\"selectPopupList\"", "</select>");
		String detail = between(code, "id=\"selectPopupById\"", "</select>");
		String insert = between(code, "id=\"insertPopup\"", "</insert>");
		String update = between(code, "id=\"updatePopup\"", "</update>");
		String toggle = between(code, "id=\"updatePopupExposeYn\"", "</update>");
		String softDelete = between(code, "id=\"softDeletePopup\"", "</update>");
		String houseExists = between(code, "id=\"selectActiveHouseExists\"", "</select>");

		// 목록 — 미삭제 + 노출위치/제목 필터 + 결정적 정렬
		assertTrue(list.contains("DEL_YN = 'N'"), "목록 미삭제 가드");
		assertTrue(list.contains("EXPOSE_LOCATION_CD = #{exposeLocationCd}"), "노출위치 필터");
		assertTrue(list.contains("TITLE LIKE"), "제목 필터");
		assertTrue(list.contains("ORDER BY CONTENT_POPUP_SN DESC"), "결정적 정렬(SN DESC)");
		// 단건/수정/토글/삭제 — 미삭제 가드
		assertTrue(detail.contains("DEL_YN = 'N'"), "단건 미삭제 가드");
		assertTrue(update.contains("DEL_YN = 'N'"), "수정 미삭제 가드");
		assertTrue(toggle.contains("DEL_YN = 'N'"), "토글 미삭제 가드");
		assertTrue(softDelete.contains("DEL_YN = 'N'"), "삭제 미삭제 가드");
		// 등록 — REG_DT=MDFCN_DT=UTC_TIMESTAMP(6)
		assertTrue(insert.contains("UTC_TIMESTAMP(6), #{rgtrSn}, UTC_TIMESTAMP(6)"), "등록 REG_DT=MDFCN_DT");
		// 삭제 = soft delete
		assertTrue(softDelete.contains("DEL_YN = 'Y'"), "삭제는 soft delete");
		// status 미저장(서비스 산출)
		assertFalse(code.contains("STTS_CD") || code.contains("STATUS"), "status 는 저장 안 함(서비스 산출)");
		// PRODUCT 존재검증 — 미삭제 고택
		assertTrue(houseExists.contains("HOUSE") && houseExists.contains("DEL_YN = 'N'"), "PRODUCT 검증 미삭제 고택");
	}

	@Test
	@DisplayName("콘텐츠 추천고택 매퍼 계약 — 미삭제 가드·HOUSE 조인·분류필터·순서정렬·수정 SORT_ORDER 보존·soft delete·고택 존재검증")
	void contentRecHouse_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("content/ContentRecHouseMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String list = between(code, "id=\"selectRecHouseList\"", "</select>");
		String detail = between(code, "id=\"selectRecHouseById\"", "</select>");
		String insert = between(code, "id=\"insertRecHouse\"", "</insert>");
		String update = between(code, "id=\"updateRecHouse\"", "</update>");
		String order = between(code, "id=\"updateRecHouseSortOrder\"", "</update>");
		String softDelete = between(code, "id=\"softDeleteRecHouse\"", "</update>");
		String houseExists = between(code, "id=\"selectActiveHouseExists\"", "</select>");

		// 목록 — 미삭제 + HOUSE 조인(고택명/주소) + 분류필터 + 결정적 정렬
		assertTrue(list.contains("R.DEL_YN = 'N'"), "목록 미삭제 가드");
		assertTrue(code.contains("LEFT JOIN HOUSE"), "고택 조인(고택명/주소, 공유 sql 조각)");
		assertTrue(list.contains("REC_CATEGORY_CD = #{recCategoryCd}"), "분류 필터");
		assertTrue(list.contains("ORDER BY R.SORT_ORDER ASC, R.CONTENT_REC_HOUSE_SN DESC"), "결정적 정렬(SORT_ORDER, SN DESC)");
		// 단건/수정/삭제 — 미삭제 가드
		assertTrue(detail.contains("R.DEL_YN = 'N'"), "단건 미삭제 가드");
		assertTrue(update.contains("DEL_YN = 'N'"), "수정 미삭제 가드");
		assertTrue(softDelete.contains("DEL_YN = 'N'"), "삭제 미삭제 가드");
		// 수정은 SORT_ORDER 미변경(순서저장 별도), 순서저장만 SORT_ORDER SET
		assertFalse(update.contains("SORT_ORDER"), "수정은 SORT_ORDER 보존(순서저장 별도)");
		assertTrue(order.contains("SORT_ORDER = #{sortOrder}"), "순서저장만 SORT_ORDER 갱신");
		// 등록 REG_DT=MDFCN_DT, 삭제 soft
		assertTrue(insert.contains("UTC_TIMESTAMP(6), #{rgtrSn}, UTC_TIMESTAMP(6)"), "등록 REG_DT=MDFCN_DT");
		assertTrue(softDelete.contains("DEL_YN = 'Y'"), "삭제는 soft delete");
		// status 미저장 + 고택 존재검증
		assertFalse(code.contains("STTS_CD") || code.contains("STATUS"), "status 는 저장 안 함(서비스 산출)");
		assertTrue(houseExists.contains("HOUSE") && houseExists.contains("DEL_YN = 'N'"), "고택 존재검증 미삭제");
	}

	@Test
	@DisplayName("콘텐츠 즐길거리 매퍼 계약 — 미삭제 가드·4필터·페이지네이션·count·수정 조회수/댓글수 보존·soft delete·status 미저장")
	void contentRegion_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("content/ContentRegionMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String where = between(code, "<sql id=\"regionListWhere\">", "</sql>");
		String list = between(code, "id=\"selectRegionList\"", "</select>");
		String count = between(code, "id=\"countRegion\"", "</select>");
		String detail = between(code, "id=\"selectRegionById\"", "</select>");
		String insert = between(code, "id=\"insertRegion\"", "</insert>");
		String update = between(code, "id=\"updateRegion\"", "</update>");
		String softDelete = between(code, "id=\"softDeleteRegion\"", "</update>");

		// 공용 필터 — 미삭제 + 4필터
		assertTrue(where.contains("DEL_YN = 'N'"), "필터 미삭제 가드");
		assertTrue(where.contains("CATEGORY_CD = #{categoryCd}"), "카테고리 필터");
		assertTrue(where.contains("TYPE_CD = #{typeCd}"), "유형 필터");
		assertTrue(where.contains("SIDO_REGION_CD = #{sidoRegionCd}"), "지역 필터");
		assertTrue(where.contains("TITLE LIKE"), "제목 필터");
		// 목록 — 필터조각 재사용 + 페이지네이션 + 정렬. count 도 동일 필터조각
		assertTrue(list.contains("<include refid=\"regionListWhere\"/>"), "목록 공용필터 재사용");
		assertTrue(list.contains("LIMIT #{limit} OFFSET #{offset}"), "오프셋 페이지네이션");
		assertTrue(list.contains("ORDER BY CONTENT_REGION_SN DESC"), "결정적 정렬(SN DESC)");
		assertTrue(count.contains("<include refid=\"regionListWhere\"/>") && count.contains("COUNT(*)"), "count 동일 필터");
		// 단건/수정/삭제 — 미삭제 가드
		assertTrue(detail.contains("DEL_YN = 'N'"), "단건 미삭제 가드");
		assertTrue(update.contains("DEL_YN = 'N'"), "수정 미삭제 가드");
		assertTrue(softDelete.contains("DEL_YN = 'N'"), "삭제 미삭제 가드");
		// 수정은 조회수/댓글수(VIEW_CNT/CMNT_CNT) 보존 — SET 절에 없음
		assertFalse(update.contains("VIEW_CNT"), "수정은 VIEW_CNT 보존");
		assertFalse(update.contains("CMNT_CNT"), "수정은 CMNT_CNT 보존");
		// 등록 REG_DT=MDFCN_DT, 삭제 soft, status 미저장
		assertTrue(insert.contains("UTC_TIMESTAMP(6), #{rgtrSn}, UTC_TIMESTAMP(6)"), "등록 REG_DT=MDFCN_DT");
		assertTrue(softDelete.contains("DEL_YN = 'Y'"), "삭제는 soft delete");
		assertFalse(code.contains("STTS_CD") || code.contains("STATUS"), "status 는 저장 안 함(서비스 산출)");
	}

	@Test
	@DisplayName("쿠폰 매퍼 계약 — 미삭제 가드·필터(할인유형/제목/상태 날짜조건)·페이지네이션·count·수정 발급/사용수 보존·soft delete·status 미저장")
	void coupon_mapperContract() throws IOException {
		String code = Files.readString(
						Path.of(System.getProperty("basedir", "."), "src", "main", "resources",
								"egovframework", "mapper", "eeum", "coupon", "CouponMapper_mysql.xml").normalize())
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String where = between(code, "<sql id=\"couponListWhere\">", "</sql>");
		String list = between(code, "id=\"selectCouponList\"", "</select>");
		String count = between(code, "id=\"countCoupon\"", "</select>");
		String detail = between(code, "id=\"selectCouponById\"", "</select>");
		String insert = between(code, "id=\"insertCoupon\"", "</insert>");
		String update = between(code, "id=\"updateCoupon\"", "</update>");
		String softDelete = between(code, "id=\"softDeleteCoupon\"", "</update>");

		// 공용 필터 — 미삭제 + 할인유형/제목 + 상태(사용기간 날짜조건)
		assertTrue(where.contains("DEL_YN = 'N'"), "필터 미삭제 가드");
		assertTrue(where.contains("DISCOUNT_TYPE_CD = #{discountTypeCd}"), "할인유형 필터");
		assertTrue(where.contains("TITLE LIKE"), "제목 필터");
		assertTrue(where.contains("USAGE_BGN_DATE") && where.contains("USAGE_END_DATE"), "상태=사용기간 날짜조건");
		// 목록 — 필터조각 재사용 + 페이지네이션 + 정렬. count 도 동일 필터조각
		assertTrue(list.contains("<include refid=\"couponListWhere\"/>"), "목록 공용필터 재사용");
		assertTrue(list.contains("LIMIT #{limit} OFFSET #{offset}"), "오프셋 페이지네이션");
		assertTrue(list.contains("ORDER BY COUPON_SN DESC"), "결정적 정렬(SN DESC)");
		assertTrue(count.contains("<include refid=\"couponListWhere\"/>") && count.contains("COUNT(*)"), "count 동일 필터");
		// 단건/수정/삭제 — 미삭제 가드
		assertTrue(detail.contains("DEL_YN = 'N'"), "단건 미삭제 가드");
		assertTrue(update.contains("DEL_YN = 'N'"), "수정 미삭제 가드");
		assertTrue(softDelete.contains("DEL_YN = 'N'"), "삭제 미삭제 가드");
		// 수정은 발급수/사용수(ISSUED_CNT/USED_CNT) 보존 — SET 절에 없음
		assertFalse(update.contains("ISSUED_CNT"), "수정은 ISSUED_CNT 보존");
		assertFalse(update.contains("USED_CNT"), "수정은 USED_CNT 보존");
		// 등록 REG_DT=MDFCN_DT, 삭제 soft, status 미저장
		assertTrue(insert.contains("UTC_TIMESTAMP(6), #{rgtrSn}, UTC_TIMESTAMP(6)"), "등록 REG_DT=MDFCN_DT");
		assertTrue(softDelete.contains("DEL_YN = 'Y'"), "삭제는 soft delete");
		assertFalse(code.contains("STTS_CD") || code.contains("STATUS"), "status 는 저장 안 함(서비스 산출)");
	}

	@Test
	@DisplayName("시스템 설정 매퍼 계약 — 단일행(SN=1) 조회/저장, 연동목록 정렬")
	void system_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("system/SystemMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String select = between(code, "id=\"selectSetting\"", "</select>");
		String update = between(code, "id=\"updateSetting\"", "</update>");
		String integrations = between(code, "id=\"selectIntegrations\"", "</select>");

		// 단일행(SN=1) 조회/저장
		assertTrue(select.contains("SYSTEM_SETTING_SN = 1"), "설정 조회는 단일행(SN=1)");
		assertTrue(update.contains("SYSTEM_SETTING_SN = 1"), "설정 저장은 단일행(SN=1)");
		// 저장은 서비스명·보안값 SET + 수정일 갱신
		assertTrue(update.contains("SERVICE_NM = #{serviceNm}"), "서비스명 저장");
		assertTrue(update.contains("AUTO_LOGOUT_MIN = #{autoLogoutMin}"), "자동로그아웃 저장");
		assertTrue(update.contains("PSWD_CHG_CYCLE_CD = #{pswdChgCycleCd}"), "비번주기 저장");
		assertTrue(update.contains("MDFCN_DT = UTC_TIMESTAMP(6)"), "수정일 갱신");
		// 연동목록 정렬
		assertTrue(integrations.contains("ORDER BY SORT_ORDER"), "연동목록 표시순서 정렬");
	}

	@Test
	@DisplayName("공지 매퍼 계약 — 미삭제 가드·4필터·페이지네이션·수정 상태보존·종료 상태가드·cron 승격 조건")
	void notice_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("notice/NoticeMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String where = between(code, "<sql id=\"noticeListWhere\">", "</sql>");
		String list = between(code, "id=\"selectNoticeList\"", "</select>");
		String update = between(code, "id=\"updateNotice\"", "</update>");
		String close = between(code, "id=\"closeNotice\"", "</update>");
		String promote = between(code, "id=\"promoteScheduled\"", "</update>");
		String softDelete = between(code, "id=\"softDeleteNotice\"", "</update>");

		// 필터 — 미삭제 + 상태/대상/알림/제목
		assertTrue(where.contains("DEL_YN = 'N'"), "필터 미삭제 가드");
		assertTrue(where.contains("NOTICE_STTS_CD = #{status}"), "상태 필터");
		assertTrue(where.contains("TARGET_TYPE_CD = #{target}"), "대상 필터");
		assertTrue(where.contains("NOTIFY_SEND_YN = #{notifySend}"), "알림 필터");
		assertTrue(where.contains("TITLE LIKE"), "제목 필터");
		assertTrue(list.contains("LIMIT #{limit} OFFSET #{offset}"), "오프셋 페이지네이션");
		// 수정은 상태/발행일/알림여부 보존(SET 절에 없음)
		assertFalse(update.contains("NOTICE_STTS_CD"), "수정은 상태 보존");
		assertFalse(update.contains("PUBLISHED_AT"), "수정은 발행일 보존");
		assertFalse(update.contains("NOTIFY_SEND_YN"), "수정은 알림여부 보존");
		// 종료는 게시중 상태 가드
		assertTrue(close.contains("NOTICE_STTS_CD = 'CLOSED'") && close.contains("NOTICE_STTS_CD = 'PUBLISHED_ONGOING'"),
				"종료는 게시중→종료 + 상태 가드");
		// cron 승격 — SCHEDULED + 예약시각 도래 → 게시중+발행일+알림Y
		assertTrue(promote.contains("NOTICE_STTS_CD = 'SCHEDULED'") && promote.contains("SCHEDULED_AT &lt;= UTC_TIMESTAMP(6)"),
				"승격 조건(SCHEDULED + 예약시각 도래)");
		assertTrue(promote.contains("NOTICE_STTS_CD = 'PUBLISHED_ONGOING'") && promote.contains("NOTIFY_SEND_YN = 'Y'"),
				"승격 시 게시중+알림Y");
		// 삭제 soft
		assertTrue(softDelete.contains("DEL_YN = 'Y'"), "삭제는 soft delete");
	}

	@Test
	@DisplayName("DM 매퍼 계약 — 미삭제 가드·3필터·페이지네이션·soft delete·cron 발송 조건")
	void dm_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("dm/DmMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String where = between(code, "<sql id=\"dmListWhere\">", "</sql>");
		String list = between(code, "id=\"selectDmList\"", "</select>");
		String softDelete = between(code, "id=\"softDeleteDm\"", "</update>");
		String promote = between(code, "id=\"promoteScheduled\"", "</update>");

		assertTrue(where.contains("DEL_YN = 'N'"), "필터 미삭제 가드");
		assertTrue(where.contains("DM_STTS_CD = #{status}"), "상태 필터");
		assertTrue(where.contains("CHANNEL_CD = #{channelCd}"), "채널 필터");
		assertTrue(where.contains("TITLE LIKE"), "제목 필터");
		assertTrue(list.contains("LIMIT #{limit} OFFSET #{offset}"), "오프셋 페이지네이션");
		assertTrue(softDelete.contains("DEL_YN = 'Y'"), "삭제는 soft delete");
		// cron 발송 — SCHEDULED + 예약시각 도래 → SENT + 발송일
		assertTrue(promote.contains("DM_STTS_CD = 'SCHEDULED'") && promote.contains("SCHEDULED_AT &lt;= UTC_TIMESTAMP(6)"),
				"발송 조건(SCHEDULED + 예약시각 도래)");
		assertTrue(promote.contains("DM_STTS_CD = 'SENT'") && promote.contains("SENT_AT = UTC_TIMESTAMP(6)"),
				"발송 시 SENT+발송일");
	}

	@Test
	@DisplayName("운영자 예약 콘솔 매퍼 계약 — 미삭제 가드·필터·페이지네이션·count + 게스트 전화(암호화 PII) 미노출")
	void operatorReservation_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("reservation/OperatorReservationMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String where = between(code, "<sql id=\"opReservationWhere\">", "</sql>");
		String list = between(code, "id=\"selectOperatorReservationList\"", "</select>");
		String count = between(code, "id=\"countOperatorReservation\"", "</select>");

		// 필터 — 미삭제 + 상태/기간(체크인)/키워드(고택명·주문번호·예약자명 EXISTS)
		assertTrue(where.contains("R.DEL_YN = 'N'"), "필터 미삭제 가드");
		assertTrue(where.contains("R.RESERVATION_STTS_CD = #{status}"), "상태 필터");
		assertTrue(where.contains("R.CHECK_IN_DATE"), "기간(체크인) 필터");
		assertTrue(where.contains("H.HOUSE_NM LIKE") && where.contains("GK.GUEST_NM LIKE"), "키워드=고택명/예약자명");
		// 목록 페이지네이션 + count 동일 필터
		assertTrue(list.contains("LIMIT #{limit} OFFSET #{offset}"), "오프셋 페이지네이션");
		assertTrue(count.contains("<include refid=\"opReservationWhere\"/>") && count.contains("COUNT(*)"), "count 동일 필터");
		// ★게스트 전화(암호화 PII)는 어떤 쿼리에서도 SELECT 하지 않는다(대량 복호화/노출 차단)
		assertFalse(code.contains("GUEST_MBL_TELNO"), "게스트 전화(암호화 PII) 미노출");
	}

	@Test
	@DisplayName("정산 매퍼 계약 — 단일행(SN=1) 설정 조회/저장·APPROVED 판매집계·회차파트너 그룹핑·실행 COMPLETED 스냅샷")
	void settlement_mapperContract() throws IOException {
		String code = Files.readString(MAPPER_ROOT.resolve("settlement/SettlementMapper_mysql.xml"))
				.replaceAll("(?s)<!--.*?-->", "").replaceAll("\\s+", " ");
		String select = between(code, "id=\"selectSetting\"", "</select>");
		String update = between(code, "id=\"updateSetting\"", "</update>");
		String rows = between(code, "id=\"selectPartnerSettlementRows\"", "</select>");
		String sales = between(code, "id=\"selectPartnerSalesForRound\"", "</select>");
		String insert = between(code, "id=\"insertPartnerSettlement\"", "</insert>");

		// 설정 = 단일행(SN=1) 조회/저장 + 수정일 갱신, 수수료율 저장
		assertTrue(select.contains("SETTLEMENT_SETTING_SN = 1"), "설정 조회는 단일행(SN=1)");
		assertTrue(update.contains("SETTLEMENT_SETTING_SN = 1"), "설정 저장은 단일행(SN=1)");
		assertTrue(update.contains("PLATFORM_FEE_RATE   = #{platformFeeRate}".replaceAll("\\s+", " ")),
				"플랫폼 수수료율 저장");
		assertTrue(update.contains("MDFCN_DT            = UTC_TIMESTAMP(6)".replaceAll("\\s+", " ")), "수정일 갱신");

		// 판매집계 = APPROVED 결제·미삭제·체크아웃월·회차파트너 그룹핑(수수료/정산금은 서비스 산출 — 미저장)
		assertTrue(rows.contains("PM.PAYMENT_STTS_CD = 'APPROVED'"), "집계는 APPROVED 결제만");
		assertTrue(rows.contains("R.DEL_YN = 'N'"), "미삭제 예약만 집계");
		assertTrue(rows.contains("DATE_FORMAT(R.CHECK_OUT_DATE, '%Y-%m') = #{roundYm}"), "체크아웃월=회차 기준");
		assertTrue(rows.contains("GROUP BY R.PRTNR_SN"), "파트너 단위 집계");
		assertTrue(sales.contains("PM.PAYMENT_STTS_CD = 'APPROVED'") && sales.contains("R.PRTNR_SN = #{prtnrSn}"),
				"실행 스냅샷 판매합도 APPROVED·해당 파트너");

		// 실행기록 = COMPLETED 스냅샷 + 등록일시
		assertTrue(insert.contains("'COMPLETED'"), "실행기록은 COMPLETED 상태");
		assertTrue(insert.contains("REG_DT") && insert.contains("UTC_TIMESTAMP(6)"), "등록일시 서버시각");
	}

	/** {@code startToken}~{@code endToken}(포함) 사이 블록 추출. 토큰 부재 시 실패. */
	private static String between(String xml, String startToken, String endToken) {
		int s = xml.indexOf(startToken);
		assertTrue(s >= 0, "시작 토큰 없음: " + startToken);
		int e = xml.indexOf(endToken, s);
		assertTrue(e >= 0, "종료 토큰 없음: " + endToken);
		return xml.substring(s, e + endToken.length());
	}
}
