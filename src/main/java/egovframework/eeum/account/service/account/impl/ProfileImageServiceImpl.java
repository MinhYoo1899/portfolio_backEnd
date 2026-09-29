package egovframework.eeum.account.service.account.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.account.service.account.ProfileImagePolicyProperties;
import egovframework.eeum.account.service.account.ProfileImageService;
import egovframework.eeum.account.web.dto.ProfileImageResponse;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.ImageContentValidator;
import egovframework.eeum.file.service.file.ImageReencoder;
import egovframework.eeum.file.service.file.ImageStorePolicy;
import egovframework.eeum.file.service.file.InMemoryMultipartFile;
import egovframework.eeum.file.web.dto.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 프로필 이미지 서비스 구현 — 즉시 연결(업로드 한 번으로 교체 완료).
 *
 * <p>흐름: EXIF/GPS 제거 재인코딩 → 공통 저장(PROFILE_IMAGE, 본인 인증만) →
 * USER_DTL_INFO.PRFL_IMG_FILE_SN 행 잠금 교체 → 이전 파일 soft delete(물리삭제 best-effort).
 *
 * <p>저장(SFTP)은 느려서 트랜잭션 밖에서 수행(파일 도메인 정책과 동일 — 메타는 autocommit, 실패 시 보상삭제).
 * DB 교체(행잠금 SELECT FOR UPDATE → UPDATE → 이전파일 soft delete)만 트랜잭션으로 묶어 동시 교체 정합성을 보장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageServiceImpl implements ProfileImageService {

	private static final String SCOPE_PROFILE_IMAGE = "PROFILE_IMAGE";
	private static final String TRGT_TYPE_USER_PRFL = "USER_PRFL";

	private final FileService fileService;
	private final ImageReencoder imageReencoder;
	private final ImageContentValidator imageValidator;
	private final ProfileImagePolicyProperties policy;
	private final ProfileImageSwapper profileImageSwapper;

	@Override
	@Audit(actn = "PROFILE_IMAGE_UPDATE", trgtType = "USER_DTL_INFO", trgtId = "#userSn")
	public ProfileImageResponse replace(long userSn, MultipartFile file) {
		// 0) 싼 검증부터(빈 파일/크기/확장자/매직바이트) — 비싼 재인코딩(디코딩) 비용 전에 빠르게 거부(codex Medium 4)
		if (file == null || file.isEmpty()) {
			throw new BusinessException(FileErrorCode.FILE_EMPTY);
		}
		if (file.getSize() > policy.maxSizeBytes()) {
			throw new BusinessException(FileErrorCode.FILE_TOO_LARGE);
		}
		// 원본 확장자/매직바이트 검증 — ImageIO 가 읽을 수 있는 GIF/BMP/WBMP 등이 재인코딩으로 우회 통과하는 것 차단(codex High 2).
		// 허용 목록(jpg/jpeg/png)에 없으면 거부, 헤더 시그니처가 확장자와 불일치해도 거부(확장자 위조 차단).
		String originalExt = extensionOf(file.getOriginalFilename());
		if (!policy.getAllowedExtensions().contains(originalExt)) {
			throw new BusinessException(FileErrorCode.FILE_EXTENSION_NOT_ALLOWED);
		}
		if (!imageValidator.matches(originalExt, readHeader(file))) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}

		// 1) EXIF/GPS 제거 — 디코드 후 재인코딩(픽셀폭탄 방어를 위해 디코딩 전 해상도 상한 검사)
		ImageReencoder.Result reencoded = imageReencoder.reencode(file, policy.getMaxPixels());
		String ext = ImageReencoder.normalizeExtension(reencoded.formatName());
		MultipartFile sanitized = new InMemoryMultipartFile(
				"file", "profile." + ext, reencoded.mimeType(), reencoded.bytes());

		// 2) 공통 저장(PROFILE_IMAGE) — 본인 인증만으로 공개 이미지 저장(FILE_UPLOAD 권한 불요)
		FileUploadResponse stored = fileService.storeImage(
				sanitized, userSn, TRGT_TYPE_USER_PRFL, String.valueOf(userSn), profileImagePolicy(ext));

		// 3) DB 교체(행 잠금) + 이전 파일 soft delete — 별도 빈(프록시 경계)에서 트랜잭션
		try {
			profileImageSwapper.swap(userSn, stored.atchFileSn());
		} catch (RuntimeException e) {
			// 교체 실패 시 방금 저장한 새 파일을 정리(고아 방지) — best-effort
			fileService.softDeleteByAtchFileSn(stored.atchFileSn(), userSn);
			throw e;
		}
		return ProfileImageResponse.from(stored);
	}

	/** 프로필 정책 → 공통 저장 정책. 확장자는 재인코딩 출력 포맷으로 고정(허용 목록도 그 한 가지). */
	private ImageStorePolicy profileImagePolicy(String ext) {
		return new ImageStorePolicy(SCOPE_PROFILE_IMAGE, "profile",
				policy.maxSizeBytes(), java.util.List.of(ext), policy.getMaxPixels(),
				policy.getDailyMaxCount(), policy.dailyMaxBytes());
	}

	/** 매직바이트 검사용 선행 바이트만 읽는다(전체 메모리 적재 금지). */
	private byte[] readHeader(MultipartFile file) {
		try (InputStream in = file.getInputStream()) {
			byte[] buf = new byte[ImageContentValidator.HEADER_BYTES];
			int read = in.readNBytes(buf, 0, buf.length);
			return read == buf.length ? buf : Arrays.copyOf(buf, read);
		} catch (IOException e) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}
	}

	/** 원본 파일명에서 확장자(소문자)를 추출 — 없으면 빈 문자열. */
	private static String extensionOf(String filename) {
		if (filename == null) {
			return "";
		}
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) {
			return "";
		}
		return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
