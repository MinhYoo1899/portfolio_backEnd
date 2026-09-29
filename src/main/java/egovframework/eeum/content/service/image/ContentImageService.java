package egovframework.eeum.content.service.image;

import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.content.web.dto.ContentImageUploadResponse;

/**
 * 콘텐츠 이미지 업로드 — 기존 파일 인프라(/files) 재사용 얇은 wrapper.
 * 정책: 확장자 jpg/jpeg/png/webp, 10MB, scope content/banner. (webp=CT-W1.1, gif 제외)
 */
public interface ContentImageService {

	/** 배너 이미지 업로드 → 자체 인프라 반환 경로({imageUrl}). */
	ContentImageUploadResponse uploadBanner(MultipartFile file, AuthUser user);
}
