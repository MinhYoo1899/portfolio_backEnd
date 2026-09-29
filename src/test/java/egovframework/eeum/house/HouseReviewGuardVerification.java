package egovframework.eeum.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import egovframework.eeum.house.service.house.HouseReviewGuard;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.vo.HouseReviewHstryVO;

/**
 * 검수 dirty 가드 검증 — REVIEW_REQUESTED 고택이 수정되면 DRAFT 복귀(+REVERT 이력), 그 외 상태는 no-op(무비용).
 *
 * <p>Codex 지적(편집시 DRAFT 복귀 계약) 방어: 편집 경로의 active-only 가드는 delYn+소유권만 검사하므로
 * REVIEW_REQUESTED 편집은 막히지 않고 이 가드까지 도달한다 — 여기서 실제 복귀·이력이 남는지 검증한다.
 */
class HouseReviewGuardVerification {

	private final HouseMapper houseMapper = mock(HouseMapper.class);
	private final HouseReviewGuard guard = new HouseReviewGuard(houseMapper);

	@Test
	@DisplayName("REVIEW_REQUESTED 고택 수정 → DRAFT 전이 + REVERT 이력")
	void revertsWhenUnderReview() {
		when(houseMapper.selectHouseSttsCd(5L)).thenReturn("REVIEW_REQUESTED");
		when(houseMapper.transitionHouseReviewStatus(5L, "REVIEW_REQUESTED", "DRAFT", 9L)).thenReturn(1);

		guard.markDirtyIfUnderReview(5L, 9L);

		verify(houseMapper).transitionHouseReviewStatus(5L, "REVIEW_REQUESTED", "DRAFT", 9L);
		ArgumentCaptor<HouseReviewHstryVO> cap = ArgumentCaptor.forClass(HouseReviewHstryVO.class);
		verify(houseMapper).insertHouseReviewHstry(cap.capture());
		assertEquals("REVERT", cap.getValue().getRvwActnCd());
		assertEquals("REVIEW_REQUESTED", cap.getValue().getBfrSttsCd());
		assertEquals("DRAFT", cap.getValue().getAftSttsCd());
	}

	@Test
	@DisplayName("DRAFT 고택 수정 → no-op(전이/이력 없음)")
	void noopWhenDraft() {
		when(houseMapper.selectHouseSttsCd(5L)).thenReturn("DRAFT");
		guard.markDirtyIfUnderReview(5L, 9L);
		verify(houseMapper, never()).transitionHouseReviewStatus(anyLong(), any(), any(), anyLong());
		verify(houseMapper, never()).insertHouseReviewHstry(any());
	}

	@Test
	@DisplayName("ACTIVE 고택 수정 → no-op")
	void noopWhenActive() {
		when(houseMapper.selectHouseSttsCd(5L)).thenReturn("ACTIVE");
		guard.markDirtyIfUnderReview(5L, 9L);
		verify(houseMapper, never()).transitionHouseReviewStatus(anyLong(), any(), any(), anyLong());
	}

	@Test
	@DisplayName("전이 경합(0건)이면 이력 남기지 않음")
	void noHistoryOnConcurrentLoss() {
		when(houseMapper.selectHouseSttsCd(5L)).thenReturn("REVIEW_REQUESTED");
		when(houseMapper.transitionHouseReviewStatus(5L, "REVIEW_REQUESTED", "DRAFT", 9L)).thenReturn(0);
		guard.markDirtyIfUnderReview(5L, 9L);
		verify(houseMapper, never()).insertHouseReviewHstry(any());
	}
}
