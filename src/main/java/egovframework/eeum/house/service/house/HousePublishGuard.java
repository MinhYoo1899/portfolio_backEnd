package egovframework.eeum.house.service.house;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;

import org.springframework.stereotype.Component;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.exception.HouseNotPublishableException;
import egovframework.eeum.house.exception.PublishCondition;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.vo.HouseVO;
import lombok.RequiredArgsConstructor;

/**
 * 발행 후 표류 방지 가드 — 이미 ACTIVE(게시중)인 고택의 편집을 "비퇴행(non-regression)" 기준으로 검증한다.
 *
 * <p>편집 코어는 mutation "전" {@link #snapshotIfActive} 로 readiness 스냅샷을 캡처하고, mutation "후"
 * (같은 {@code @Transactional}) {@link #enforceNoRegression} 를 호출한다. 변경 전 충족이던 <b>원시 조건</b>이
 * 변경 후 미충족으로 <b>전환</b>된 경우에만 {@code HOUSE_NOT_PUBLISHABLE}(409)로 던져 트랜잭션을 롤백한다.
 *
 * <ul>
 *   <li>이미 미충족이던 다른 조건 때문에 <b>개선 작업</b>이 막히지 않는다(교착 해소).</li>
 *   <li>개선하면서 다른 충족 조건을 새로 깨뜨리면 거부된다(퇴행 차단).</li>
 *   <li>완전 충족 고택을 미충족으로 만드는 파괴적 변경은 거부된다.</li>
 *   <li>대상이 ACTIVE 가 아니면 캡처가 {@code null} → enforce 는 no-op(비ACTIVE 기존 동작 보존).</li>
 * </ul>
 *
 * <p><b>동시성</b>: {@link #snapshotIfActive} 는 스냅샷 전에 부모 HOUSE 행을 {@code FOR UPDATE} 로 잠근다
 * (재진입 — 코어가 이미 HOUSE→ROOM 순서로 선잠금했으면 무블록). 동일 고택의 서로 다른 객실 mutation 이 직렬화되며,
 * owner/DEL_YN/status 검증도 잠긴 행 기준으로 한다. 새 락순서 엣지를 만들지 않아(HOUSE 가 항상 최상위 락) 교착을 늘리지 않는다.
 *
 * <p>전/후 평가는 같은 트랜잭션 + 같은 기준일({@link ReadinessSnapshot#baseDate})을 사용한다.
 */
@Component
@RequiredArgsConstructor
public class HousePublishGuard {

	private static final String Y = "Y";
	private static final String STTS_ACTIVE = "ACTIVE";

	private final HouseMapper houseMapper;
	private final HousePublishValidator publishValidator;

	/**
	 * 편집 <b>전</b> 호출 — 부모 HOUSE 행을 FOR UPDATE 로 잠그고(동일 고택 직렬화), 잠긴 행 기준으로 owner/DEL_YN 을
	 * 검증한다(불일치=fail-closed {@link HouseErrorCode#HOUSE_NOT_FOUND}). 상태가 ACTIVE 면 readiness 원시 스냅샷을
	 * 캡처해 반환, 아니면 {@code null}(이후 {@link #enforceNoRegression} no-op). 캡처 시점 기준일을 스냅샷에 담아 전/후 동일 기준일을 보장한다.
	 */
	public ReadinessSnapshot snapshotIfActive(long houseSn, long ownerPrtnrSn) {
		HouseVO locked = houseMapper.selectHouseForUpdate(houseSn); // FOR UPDATE(재진입) — 스냅샷 전 부모 HOUSE 잠금
		if (locked == null || Y.equals(locked.getDelYn())
				|| locked.getPrtnrSn() == null || !locked.getPrtnrSn().equals(ownerPrtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		if (!STTS_ACTIVE.equals(locked.getHouseSttsCd())) {
			return null; // 비ACTIVE — no-op(단 HOUSE 락은 잡아 직렬화됨)
		}
		return publishValidator.snapshot(houseSn, ownerPrtnrSn, LocalDate.now());
	}

	/**
	 * 편집 <b>후</b> 호출 — {@code before} 캡처가 있으면(편집 전 ACTIVE) 같은 기준일로 after 를 재평가하고,
	 * 충족→미충족으로 전환된 원시 조건이 있으면 그 롤업 카테고리들을 담아 409(롤백)로 던진다. after 스냅샷 구성 중
	 * 고택 유실/삭제/소유권 불일치면 {@link HousePublishValidator#snapshot} 이 fail-closed 로 throw 한다(롤백).
	 * {@code before} 가 {@code null}(편집 전 비ACTIVE)이면 아무것도 하지 않는다.
	 */
	public void enforceNoRegression(long houseSn, long ownerPrtnrSn, ReadinessSnapshot before) {
		if (before == null) {
			return; // 편집 전 비ACTIVE — 기존 no-op 동작 보존
		}
		ReadinessSnapshot after = publishValidator.snapshot(houseSn, ownerPrtnrSn, before.baseDate());
		Set<PublishCondition> regressed = before.regressionsAgainst(after);
		if (!regressed.isEmpty()) {
			throw new HouseNotPublishableException(new ArrayList<>(regressed));
		}
	}
}
