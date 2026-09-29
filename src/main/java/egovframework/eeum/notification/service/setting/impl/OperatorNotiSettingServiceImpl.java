package egovframework.eeum.notification.service.setting.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.notification.service.setting.OperatorNotiSettingService;
import egovframework.eeum.notification.service.setting.dao.OperatorNotiSettingMapper;
import egovframework.eeum.notification.vo.OperatorNotiSettingVO;
import egovframework.eeum.notification.web.dto.NotificationSettingsResponse;
import egovframework.eeum.notification.web.dto.NotificationSettingsUpdateRequest;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 알림 설정 서비스 구현 — 대상은 항상 토큰 본인(USER_SN). 행 부재 시 기본값 반환, 저장은 전체교체 upsert.
 *
 * <p>설정 변경은 <b>이후 알림 생성</b>에만 영향(§③ 적재 훅)이며, <b>기존 적재된 알림은 삭제하지 않는다</b>(저장소 분리).
 */
@Service
@RequiredArgsConstructor
public class OperatorNotiSettingServiceImpl implements OperatorNotiSettingService {

	private final OperatorNotiSettingMapper mapper;

	@Override
	@Transactional(readOnly = true)
	public NotificationSettingsResponse getSettings(long userSn) {
		requireOperator(userSn);
		OperatorNotiSettingVO vo = mapper.selectByUserSn(userSn);
		return vo == null ? NotificationSettingsResponse.defaults() : NotificationSettingsResponse.from(vo);
	}

	@Override
	@Transactional
	public NotificationSettingsResponse updateSettings(long userSn, NotificationSettingsUpdateRequest request) {
		requireOperator(userSn);
		mapper.upsert(userSn,
				yn(request.reservationAlert()),
				yn(request.inquiryAlert()),
				yn(request.settlementAlert()),
				yn(request.marketingAlert()));
		// 저장한 값 그대로 확정 반환(재조회 시에도 동일 — 토글 즉시 저장 계약)
		return new NotificationSettingsResponse(
				request.reservationAlert(), request.inquiryAlert(),
				request.settlementAlert(), request.marketingAlert());
	}

	private static String yn(boolean on) {
		return on ? "Y" : "N";
	}

	/**
	 * 운영자 경계 검증 — JWT userTypeCd 만 신뢰하지 않고 DB(USER_TYPE_CD='OPERATOR' AND DEL_YN='N')로 확인.
	 * MEMBER/PARTNER/미존재 USER_SN 은 조회·저장 모두 차단(403).
	 */
	private void requireOperator(long userSn) {
		if (!mapper.existsOperator(userSn)) {
			throw BusinessException.forbidden();
		}
	}
}
