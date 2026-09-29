package egovframework.eeum.house.service.house;

import org.springframework.stereotype.Component;

import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.vo.HouseReviewHstryVO;
import lombok.RequiredArgsConstructor;

/**
 * 검수 dirty 가드 — 검수요청(REVIEW_REQUESTED) 상태의 고택이 수정되면 검수 요청이 무효화되므로 DRAFT 로 자동복귀시킨다.
 *
 * <p>결정(2026-07-06): 검수요청 후 상품 내용(고택 기본정보/정책/이미지/객실/침구·편의시설/요금·재고·캘린더/휴관일 등)이
 * 바뀌면 재검수가 필요 → 편집 차단이 아니라 DRAFT 자동복귀 + 이력(REVERT) 기록. 운영자 콘솔 콘텐츠 변경 코어(*AsOperator)가
 * 같은 {@code @Transactional} 안에서 mutation 직후 호출한다. 대상이 REVIEW_REQUESTED 가 아니면 no-op(대다수 경로 무비용:
 * 상태 1회 조회).
 */
@Component
@RequiredArgsConstructor
public class HouseReviewGuard {

	private static final String REVIEW_REQUESTED = "REVIEW_REQUESTED";
	private static final String DRAFT = "DRAFT";

	private final HouseMapper houseMapper;

	/**
	 * 대상 고택이 REVIEW_REQUESTED 면 DRAFT 로 복귀(+REVERT 이력). 그 외/미존재면 no-op.
	 *
	 * @param houseSn      대상 고택
	 * @param actorUserSn  행위자(운영자) 사용자일련번호
	 */
	public void markDirtyIfUnderReview(long houseSn, long actorUserSn) {
		if (!REVIEW_REQUESTED.equals(houseMapper.selectHouseSttsCd(houseSn))) {
			return;
		}
		if (houseMapper.transitionHouseReviewStatus(houseSn, REVIEW_REQUESTED, DRAFT, actorUserSn) > 0) {
			houseMapper.insertHouseReviewHstry(
					HouseReviewHstryVO.of(houseSn, "REVERT", REVIEW_REQUESTED, DRAFT, actorUserSn, null));
		}
	}
}
