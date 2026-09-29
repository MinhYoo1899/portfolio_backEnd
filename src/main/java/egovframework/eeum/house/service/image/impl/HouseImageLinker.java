package egovframework.eeum.house.service.image.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.house.exception.HouseErrorCode;
import egovframework.eeum.house.service.house.dao.HouseMapper;
import egovframework.eeum.house.service.image.dao.HouseImageMapper;
import egovframework.eeum.house.vo.HouseImageVO;
import egovframework.eeum.house.vo.HouseVO;
import lombok.RequiredArgsConstructor;

/**
 * 고택 이미지 연결 트랜잭션 — 업로드(트랜잭션 밖) 후 HOUSE_IMAGE 행 삽입만 별도 빈(프록시 경계)에서 트랜잭션 처리.
 *
 * <p>프로필 이미지(ProfileImageSwapper)와 동일 패턴: 느린 SFTP 는 트랜잭션 밖, DB 행은 부모 고택 잠금 아래 정합.
 */
@Component
@RequiredArgsConstructor
public class HouseImageLinker {

	private static final String Y = "Y";

	private final HouseImageMapper houseImageMapper;
	private final HouseMapper houseMapper;

	/**
	 * 부모 고택 FOR UPDATE(소유·활성 재검증) 후 이미지 행 삽입. 정렬=max+1, 첫 이미지면 자동 대표.
	 *
	 * @return 삽입된 이미지(houseImgSn/sortSeq/rprsYn/atchFileSn 세팅)
	 */
	@Transactional
	public HouseImageVO link(long houseSn, long prtnrSn, long atchFileSn, long actorUserSn) {
		HouseVO house = houseMapper.selectHouseForUpdate(houseSn);
		if (house == null || Y.equals(house.getDelYn()) || !house.getPrtnrSn().equals(prtnrSn)) {
			throw new BusinessException(HouseErrorCode.HOUSE_NOT_FOUND);
		}
		int sortSeq = houseImageMapper.maxSortSeqByHouse(houseSn) + 1;
		String rprsYn = houseImageMapper.countActiveImagesByHouse(houseSn) == 0 ? "Y" : "N";
		HouseImageVO vo = HouseImageVO.builder()
				.houseSn(houseSn)
				.atchFileSn(atchFileSn)
				.sortSeq(sortSeq)
				.rprsYn(rprsYn)
				.rgtrSn(actorUserSn)
				.build();
		houseImageMapper.insertHouseImage(vo);
		return vo;
	}
}
