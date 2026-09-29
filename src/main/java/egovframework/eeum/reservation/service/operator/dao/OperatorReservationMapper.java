package egovframework.eeum.reservation.service.operator.dao;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.reservation.vo.OperatorReservationVO;

/**
 * 운영자 예약 콘솔 조회 매퍼 — 전 판매자 예약(조회 전용). RESERVATION + HOUSE + ITEM/GUEST/PAYMENT 상관 서브쿼리 집계.
 * 게스트 전화(암호화)는 SELECT 하지 않는다(대량 복호화·PII 노출 차단).
 */
@Mapper
public interface OperatorReservationMapper {

	/** 목록(미삭제) — 상태/기간(체크인)/키워드(고택명·예약자명·주문번호) 필터 + LIMIT/OFFSET, SN DESC. */
	List<OperatorReservationVO> selectOperatorReservationList(@Param("status") String status,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
			@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

	/** 목록 총건수(미삭제, 동일 필터). */
	long countOperatorReservation(@Param("status") String status, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate, @Param("keyword") String keyword);

	/** 단건(미삭제). 없으면 null. */
	OperatorReservationVO selectOperatorReservationDetail(@Param("reservationSn") long reservationSn);
}
