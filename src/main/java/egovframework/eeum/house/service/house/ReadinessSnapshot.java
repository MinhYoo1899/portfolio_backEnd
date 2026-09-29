package egovframework.eeum.house.service.house;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import egovframework.eeum.house.exception.PublishCondition;

/**
 * 발행 readiness 원시 스냅샷 — ACTIVE 고택 편집 가드의 비퇴행(non-regression) 판정용.
 *
 * <p>발행 9조건을 "원시 조건" 단위로 분해해 {@code met}(충족여부)로 담는다:
 * 하우스 레벨 8개(파트너 승인/사업자정보, 고택 기본정보, 체크인아웃, 예약확정방식, 취소정책, 결제수단, 대표이미지)
 * + <b>객실별 6개</b>(ACTIVE/재고/욕실/침구/이미지/미래 판매가능 캘린더) + 파생 {@code HAS_PUBLISHABLE_ROOM}.
 * 각 원시 조건은 응답용 {@link PublishCondition} 카테고리로 롤업된다(객실 원시조건 → PUBLISHABLE_ROOM_MISSING).
 *
 * <p><b>비퇴행 판정</b>({@link #regressionsAgainst}): 변경 전 충족(true)이던 원시 조건이 변경 후
 * <b>존재하며 미충족(false)으로 전환</b>된 것만 "새 퇴행"으로 본다. 이미 미충족이던 조건은 개선 작업을 막지 않는다.
 * 변경 후 키가 사라진 경우(예: 삭제된 객실의 하위조건)는 전환이 아니므로 퇴행 아님 —
 * "게시가능 객실 소멸"은 하우스 레벨 {@code HAS_PUBLISHABLE_ROOM} 키가 잡는다(집계 키가 true→false 로 전환).
 *
 * <p>단순 {@link PublishCondition} 집합 비교가 아니라 원시 조건키 단위로 전/후를 비교한다(같은 기준일 기준).
 */
public final class ReadinessSnapshot {

	private final LocalDate baseDate;
	/** 원시 조건키 → 충족여부(순서 보존). */
	private final Map<String, Boolean> met;
	/** 원시 조건키 → 응답용 롤업 카테고리. */
	private final Map<String, PublishCondition> category;

	private ReadinessSnapshot(LocalDate baseDate, Map<String, Boolean> met, Map<String, PublishCondition> category) {
		this.baseDate = baseDate;
		this.met = met;
		this.category = category;
	}

	static Builder builder(LocalDate baseDate) {
		return new Builder(baseDate);
	}

	LocalDate baseDate() {
		return baseDate;
	}

	/** 원시 조건 충족여부(미존재 키는 false 취급). */
	boolean met(String key) {
		return Boolean.TRUE.equals(met.get(key));
	}

	/**
	 * 이 스냅샷(변경 전) 대비 {@code after}(변경 후)에서 <b>충족→미충족으로 전환된</b> 원시 조건들의
	 * 롤업 카테고리 집합(중복 제거·발견 순서 보존). 비어 있으면 퇴행 없음.
	 */
	Set<PublishCondition> regressionsAgainst(ReadinessSnapshot after) {
		Set<PublishCondition> regressed = new LinkedHashSet<>();
		for (Map.Entry<String, Boolean> e : met.entrySet()) {
			if (!Boolean.TRUE.equals(e.getValue())) {
				continue; // 변경 전 미충족 → 개선 대상(퇴행 판정 제외)
			}
			if (Boolean.FALSE.equals(after.met.get(e.getKey()))) { // 변경 후 존재 + 미충족으로 전환
				regressed.add(category.get(e.getKey()));
			}
		}
		return regressed;
	}

	/** 원시 조건키/카테고리/충족여부를 순서대로 쌓는 빌더. */
	static final class Builder {
		private final LocalDate baseDate;
		private final Map<String, Boolean> met = new LinkedHashMap<>();
		private final Map<String, PublishCondition> category = new LinkedHashMap<>();

		private Builder(LocalDate baseDate) {
			this.baseDate = baseDate;
		}

		Builder put(String key, boolean met, PublishCondition category) {
			this.met.put(key, met);
			this.category.put(key, category);
			return this;
		}

		ReadinessSnapshot build() {
			return new ReadinessSnapshot(baseDate, met, category);
		}
	}
}
