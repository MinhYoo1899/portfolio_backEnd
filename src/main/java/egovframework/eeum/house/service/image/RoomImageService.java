package egovframework.eeum.house.service.image;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.house.web.dto.RoomImageDeleteRequest;
import egovframework.eeum.house.web.dto.RoomImageRepresentativeRequest;
import egovframework.eeum.house.web.dto.RoomImageResponse;

/**
 * 객실 이미지 서비스(판매자) — 본인 소유(ROOM→HOUSE→PRTNR_SN) 객실의 공개 이미지 추가/조회/삭제/대표지정.
 */
public interface RoomImageService {

	RoomImageResponse addImage(long roomSn, MultipartFile file, AuthUser partner);

	List<RoomImageResponse> listByRoom(long roomSn, AuthUser partner);

	void delete(RoomImageDeleteRequest request, AuthUser partner);

	void setRepresentative(RoomImageRepresentativeRequest request, AuthUser partner);

	// ===== 운영자 대행(HOUSE_PROXY_MNG) — owner=대상객실 서버해석, 행위자=운영자 =====

	RoomImageResponse addImageAsOperator(long roomSn, MultipartFile file, AuthUser operator);

	List<RoomImageResponse> listByRoomAsOperator(long roomSn, AuthUser operator);

	void deleteAsOperator(RoomImageDeleteRequest request, AuthUser operator);

	void setRepresentativeAsOperator(RoomImageRepresentativeRequest request, AuthUser operator);
}
