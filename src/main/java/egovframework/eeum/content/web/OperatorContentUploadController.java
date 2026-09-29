package egovframework.eeum.content.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.content.service.image.ContentImageService;
import egovframework.eeum.content.web.dto.ContentImageUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 콘텐츠 이미지 업로드 — 기존 파일 인프라 재사용 wrapper. 권한 CONTENT_MNG.
 * 정책: jpg/jpeg/png/webp, 10MB, scope content/banner. 응답 imageUrl 은 배너 등록/수정에 그대로 사용.
 */
@Tag(name = "operator-content-upload", description = "운영자 콘텐츠 이미지 업로드")
@RestController
@RequestMapping("/operator/uploads")
@RequiredArgsConstructor
public class OperatorContentUploadController {

	private final ContentImageService contentImageService;

	@Operation(summary = "콘텐츠 이미지 업로드(운영자)",
			description = "권한 CONTENT_MNG. 멀티파트. jpg/jpeg/png/webp·10MB·매직바이트·픽셀상한 검증 후 저장 → { imageUrl }.")
	@PreAuthorize("hasAuthority('CONTENT_MNG')")
	@PostMapping("/images")
	public ResponseEntity<ApiResponse<ContentImageUploadResponse>> uploadImage(
			@RequestParam("file") MultipartFile file, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(contentImageService.uploadBanner(file, user)));
	}
}
