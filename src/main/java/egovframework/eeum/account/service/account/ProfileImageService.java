package egovframework.eeum.account.service.account;

import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.account.web.dto.ProfileImageResponse;

/**
 * 프로필 이미지 서비스 — 본인 프로필 이미지 업로드/교체(즉시 연결).
 *
 * <p>업로드 한 번으로 EXIF 제거 재인코딩 → 저장(PROFILE_IMAGE) → USER_DTL_INFO.PRFL_IMG_FILE_SN 교체 →
 * 이전 파일 soft delete 까지 완료한다. 클라이언트가 임의 fileSn 을 프로필로 지정하는 구조는 제공하지 않는다.
 */
public interface ProfileImageService {

	/** 본인 프로필 이미지 교체 — 대상은 항상 토큰의 본인(userSn). */
	ProfileImageResponse replace(long userSn, MultipartFile file);
}
