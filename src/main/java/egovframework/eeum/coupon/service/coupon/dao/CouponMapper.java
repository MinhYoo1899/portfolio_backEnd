package egovframework.eeum.coupon.service.coupon.dao;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.coupon.vo.CouponVO;

/**
 * 쿠폰 매퍼 — COUPON CRUD(soft delete). 목록은 할인유형/제목/상태(사용기간 날짜조건) 필터 + 오프셋 페이지네이션.
 * status 는 저장 컬럼이 아니라 사용기간 vs {@code today} 로 SQL 필터(발급수/사용수는 수정 대상 아님).
 */
@Mapper
public interface CouponMapper {

	/** 등록 — useGeneratedKeys 로 couponSn 채움. REG_DT=MDFCN_DT=UTC_TIMESTAMP(6). ISSUED/USED_CNT=0(default). */
	int insertCoupon(CouponVO vo);

	/** 목록(미삭제) — 필터 + LIMIT/OFFSET, SN DESC. status 는 today 기준 날짜조건. */
	List<CouponVO> selectCouponList(@Param("discountTypeCd") String discountTypeCd, @Param("keyword") String keyword,
			@Param("status") String status, @Param("today") LocalDate today,
			@Param("offset") int offset, @Param("limit") int limit);

	/** 목록 총건수(미삭제, 동일 필터). */
	long countCoupon(@Param("discountTypeCd") String discountTypeCd, @Param("keyword") String keyword,
			@Param("status") String status, @Param("today") LocalDate today);

	/** 단건(미삭제). 없으면 null. */
	CouponVO selectCouponById(@Param("couponSn") long couponSn);

	/** 전체 교체 수정(미삭제) — ISSUED_CNT/USED_CNT 보존. 영향행 0 = 없음. */
	int updateCoupon(CouponVO vo);

	/** soft delete(DEL_YN='Y', 미삭제만). 영향행 0 = 없음. */
	int softDeleteCoupon(@Param("couponSn") long couponSn, @Param("mdfrSn") long mdfrSn);
}
