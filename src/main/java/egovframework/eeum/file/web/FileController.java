package egovframework.eeum.file.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.file.service.file.FileDownload;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.storage.StoredResource;
import egovframework.eeum.file.web.dto.FileDeleteRequest;
import egovframework.eeum.file.web.dto.FileDetailResponse;
import egovframework.eeum.file.web.dto.FileQueryRequest;
import egovframework.eeum.file.web.dto.FileUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 파일 API — Phase 1(공개 이미지). 업로드/삭제는 인증, 조회/다운로드는 공개.
 * 저장은 SFTP(백엔드 경유 스트리밍). PRIVATE_DOC(암호화)은 Phase 2.
 */
@Slf4j
@Tag(name = "file", description = "파일 첨부 (공개 이미지 — 업로드/조회/다운로드/삭제)")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

	private final FileService fileService;

	@Operation(summary = "파일 업로드(공개 이미지)", description = "멀티파트 업로드. 운영자·파트너 권한(FILE_UPLOAD) 필요. 확장자(gif/jpg/jpeg/png)·크기(50MB)·매직바이트·픽셀상한·업로드쿼터 검증 후 SFTP 저장.")
	@PreAuthorize("hasAuthority('FILE_UPLOAD')")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<FileUploadResponse>> insert(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "trgtType", required = false) String trgtType,
			@RequestParam(value = "trgtId", required = false) String trgtId,
			@AuthenticationPrincipal AuthUser user) {
		FileUploadResponse result = fileService.upload(file, requireUser(user).userSn(), trgtType, trgtId);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
	}

	@Operation(summary = "파일 메타 조회(공개)", description = "공개 이미지 메타데이터.")
	@GetMapping("/detail")
	public ApiResponse<FileDetailResponse> detail(@Valid FileQueryRequest request) {
		return ApiResponse.ok(fileService.detail(request.atchFileSn()));
	}

	@Operation(summary = "파일 다운로드(공개)", description = "공개 이미지 바이트 스트리밍(백엔드 경유). inline 표시.")
	@GetMapping("/download")
	public ResponseEntity<StreamingResponseBody> download(@Valid FileQueryRequest request) {
		FileDownload file = fileService.download(request.atchFileSn());

		// 저장소 스트림은 람다 내부에서 지연 개방·반납 — 람다 미실행/조기 단절 시 세션 누수 방지(codex 코드검토)
		StreamingResponseBody body = outputStream -> {
			try (StoredResource resource = fileService.openStream(file.strgKey())) {
				resource.inputStream().transferTo(outputStream);
			}
		};

		ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.orgnlFileNm()))
				.header(HttpHeaders.CONTENT_TYPE, mediaType(file.mimeTypeNm()))
				.header("X-Content-Type-Options", "nosniff");
		if (file.fileSz() != null && file.fileSz() > 0) {
			builder.contentLength(file.fileSz());
		}
		return builder.body(body);
	}

	@Operation(summary = "파일 삭제", description = "soft delete. 등록자 본인 또는 운영자만.")
	@PostMapping("/delete")
	public ApiResponse<Void> delete(@Valid @RequestBody FileDeleteRequest request,
			@AuthenticationPrincipal AuthUser user) {
		fileService.delete(request.atchFileSn(), requireUser(user));
		return ApiResponse.ok();
	}

	// ------------------------------------------------------------------

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}

	private static String mediaType(String mimeTypeNm) {
		return mimeTypeNm == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : mimeTypeNm;
	}

	/** RFC 5987 — 한글/특수문자 파일명 안전 전달. 이미지는 inline 표시. */
	private static String contentDisposition(String filename) {
		String name = (filename == null || filename.isBlank()) ? "file" : filename;
		String fallback = name.replaceAll("[\"\\r\\n]", "_");
		String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
		return "inline; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
	}
}
