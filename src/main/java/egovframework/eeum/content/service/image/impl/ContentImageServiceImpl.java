package egovframework.eeum.content.service.image.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.content.service.image.ContentImageService;
import egovframework.eeum.content.web.dto.ContentImageUploadResponse;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.ImageStorePolicy;
import egovframework.eeum.file.web.dto.FileUploadResponse;
import lombok.RequiredArgsConstructor;

/**
 * 콘텐츠 이미지 업로드 구현 — 기존 파일 인프라({@link FileService#storeImage}) 재사용 얇은 wrapper.
 *
 * <p>정책(PM 확정): 확장자 jpg/jpeg/png/webp, 10MB, 저장 scope=content/banner, 공개 이미지 스코프(다운로드=/files/download 재사용).
 * webp 는 CT-W1.1 에서 지원 — 공유 검증기(ImageContentValidator)가 RIFF/WEBP 매직바이트+치수를 직접 파싱한다(gif 는 허용 목록 제외).
 */
@Service
@RequiredArgsConstructor
public class ContentImageServiceImpl implements ContentImageService {

	/** 공개 이미지 스코프 재사용 — 업로드 결과가 /files/download 로 노출되도록. */
	private static final String SCOPE_PUBLIC_IMAGE = "PUBLIC_IMAGE";
	/** 저장키 접두(스코프 경로). */
	private static final String KEY_PREFIX = "content/banner";
	/** 배너 이미지 1건 최대 10MB. */
	private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
	/** 허용 확장자(CT-W0 정책: jpg/jpeg/png/webp — gif 제외). */
	private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");
	/** 픽셀폭탄 방어 상한(공개 이미지와 동일 50MP). */
	private static final long MAX_PIXELS = 50_000_000L;
	/** 업로더 1인당 24시간 건수/용량 상한(공개 이미지와 동일). */
	private static final int DAILY_MAX_COUNT = 50;
	private static final long DAILY_MAX_BYTES = 500L * 1024 * 1024;
	/** 감사/연결 대상 유형. */
	private static final String TRGT_TYPE = "CONTENT_BANNER";

	private final FileService fileService;

	@Override
	@Audit(actn = "CONTENT_IMAGE_UPLOAD", trgtType = "CONTENT_MAIN_BANNER")
	public ContentImageUploadResponse uploadBanner(MultipartFile file, AuthUser user) {
		ImageStorePolicy policy = new ImageStorePolicy(SCOPE_PUBLIC_IMAGE, KEY_PREFIX, MAX_SIZE_BYTES,
				ALLOWED_EXTENSIONS, MAX_PIXELS, DAILY_MAX_COUNT, DAILY_MAX_BYTES);
		FileUploadResponse uploaded = fileService.storeImage(file, user.userSn(), TRGT_TYPE, null, policy);
		return new ContentImageUploadResponse(uploaded.downloadUrl());
	}
}
