package egovframework.eeum.inquiry.web;

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
import egovframework.eeum.base.response.ApiResponse;
import egovframework.eeum.file.service.file.FileDownload;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.storage.StoredResource;
import egovframework.eeum.file.web.dto.FileUploadResponse;
import egovframework.eeum.inquiry.service.inquiry.InquiryAttachmentService;
import egovframework.eeum.inquiry.service.inquiry.InquiryService;
import egovframework.eeum.inquiry.web.dto.InqrAnswerDeleteRequest;
import egovframework.eeum.inquiry.web.dto.InqrAnswerInsertRequest;
import egovframework.eeum.inquiry.web.dto.InqrAnswerUpdateRequest;
import egovframework.eeum.inquiry.web.dto.InqrBlindRequest;
import egovframework.eeum.inquiry.web.dto.InqrDeleteRequest;
import egovframework.eeum.inquiry.web.dto.InqrDetailResponse;
import egovframework.eeum.inquiry.web.dto.InqrInsertRequest;
import egovframework.eeum.inquiry.web.dto.InqrListResponse;
import egovframework.eeum.inquiry.web.dto.InqrSearchRequest;
import egovframework.eeum.inquiry.web.dto.InqrUnblindRequest;
import egovframework.eeum.inquiry.web.dto.InqrUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 문의(게시판형 CS Q&A) API.
 *
 * <p>목록/상세=공개(비밀글·블라인드 접근제어), 작성/수정/삭제=회원 본인, 답변=운영자(INQR_ANS),
 * 블라인드=운영자(INQR_MODER). 목록은 오프셋(page/pageSize)·커서(cursor/size, 무한스크롤) 지원.
 */
@Tag(name = "inquiry", description = "문의 (게시판형 CS Q&A)")
@RestController
@RequestMapping("/inquiries")
@RequiredArgsConstructor
public class InquiryController {

	private final InquiryService inquiryService;
	private final InquiryAttachmentService attachmentService;
	private final FileService fileService;

	@Operation(summary = "문의 첨부 업로드(임시)",
			description = "회원 본인. 이미지(jpg/jpeg/png) 멀티파트. EXIF/GPS 제거 재인코딩 후 임시(TEMP) 저장. "
					+ "응답의 atchFileSn 을 작성/수정 요청의 attachments 로 연결한다(24h 내 미연결 시 자동 정리).")
	@PostMapping("/files/upload")
	public ResponseEntity<ApiResponse<FileUploadResponse>> uploadAttachment(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "visibility", required = false) String visibility,
			@AuthenticationPrincipal AuthUser user) {
		FileUploadResponse result = attachmentService.uploadTemp(file, user, visibility);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
	}

	@Operation(summary = "문의 첨부 다운로드(권한판정)",
			description = "PUBLIC 첨부는 누구나, PRIVATE 첨부는 작성자·관리권한 운영자만(아니면 404). 백엔드 경유 스트리밍.")
	@GetMapping("/files/download")
	public ResponseEntity<StreamingResponseBody> downloadAttachment(
			@RequestParam("atchFileSn") Long atchFileSn,
			@AuthenticationPrincipal AuthUser user) {
		FileDownload file = attachmentService.downloadForViewer(atchFileSn, user);
		// 저장소 스트림은 람다 내부에서 지연 개방·반납(세션 누수 방지 — file 도메인과 동일 패턴)
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

	@Operation(summary = "문의글 작성", description = "회원 작성. 비밀글 미지정 시 예약/결제/계정 유형은 기본 비밀.")
	@PostMapping("/insert")
	public ResponseEntity<ApiResponse<InqrDetailResponse>> insert(
			@Valid @RequestBody InqrInsertRequest request, @AuthenticationPrincipal AuthUser user) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(inquiryService.insert(request, user)));
	}

	@Operation(summary = "문의 목록", description = "공개(비밀글 마스킹). 오프셋(page/pageSize) 또는 커서(cursor/size, 무한스크롤). 필터 mine/inqrType/ansStts/keyword.")
	@GetMapping("/list")
	public ApiResponse<InqrListResponse> list(@Valid InqrSearchRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(inquiryService.list(request, user));
	}

	@Operation(summary = "문의 상세", description = "공개글 누구나. 비밀글/블라인드는 작성자·운영자만(비인가 404).")
	@GetMapping("/detail")
	public ApiResponse<InqrDetailResponse> detail(@RequestParam("inqrSn") Long inqrSn,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(inquiryService.detail(inqrSn, user));
	}

	@Operation(summary = "문의 수정", description = "작성자 본인, 답변 전(PENDING)에만.")
	@PostMapping("/update")
	public ApiResponse<Void> update(@Valid @RequestBody InqrUpdateRequest request,
			@AuthenticationPrincipal AuthUser user) {
		inquiryService.update(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "문의 삭제", description = "작성자 본인(soft delete).")
	@PostMapping("/delete")
	public ApiResponse<Void> delete(@Valid @RequestBody InqrDeleteRequest request,
			@AuthenticationPrincipal AuthUser user) {
		inquiryService.delete(request.inqrSn(), user);
		return ApiResponse.ok();
	}

	@Operation(summary = "답변 등록(운영자)", description = "권한 INQR_ANS. 답변 시 상태 ANSWERED.")
	@PreAuthorize("hasAuthority('INQR_ANS')")
	@PostMapping("/answers/insert")
	public ApiResponse<InqrDetailResponse> answerInsert(@Valid @RequestBody InqrAnswerInsertRequest request,
			@AuthenticationPrincipal AuthUser user) {
		return ApiResponse.ok(inquiryService.answer(request, user));
	}

	@Operation(summary = "답변 수정(운영자)", description = "권한 INQR_ANS.")
	@PreAuthorize("hasAuthority('INQR_ANS')")
	@PostMapping("/answers/update")
	public ApiResponse<Void> answerUpdate(@Valid @RequestBody InqrAnswerUpdateRequest request,
			@AuthenticationPrincipal AuthUser user) {
		inquiryService.updateAnswer(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "답변 삭제(운영자)", description = "권한 INQR_ANS. 남은 답변 없으면 상태 PENDING 복귀.")
	@PreAuthorize("hasAuthority('INQR_ANS')")
	@PostMapping("/answers/delete")
	public ApiResponse<Void> answerDelete(@Valid @RequestBody InqrAnswerDeleteRequest request,
			@AuthenticationPrincipal AuthUser user) {
		inquiryService.deleteAnswer(request.ansSn(), user);
		return ApiResponse.ok();
	}

	@Operation(summary = "블라인드(운영자)", description = "권한 INQR_MODER. 부적절글 비노출.")
	@PreAuthorize("hasAuthority('INQR_MODER')")
	@PostMapping("/blind")
	public ApiResponse<Void> blind(@Valid @RequestBody InqrBlindRequest request,
			@AuthenticationPrincipal AuthUser user) {
		inquiryService.blind(request, user);
		return ApiResponse.ok();
	}

	@Operation(summary = "블라인드 해제(운영자)", description = "권한 INQR_MODER.")
	@PreAuthorize("hasAuthority('INQR_MODER')")
	@PostMapping("/unblind")
	public ApiResponse<Void> unblind(@Valid @RequestBody InqrUnblindRequest request,
			@AuthenticationPrincipal AuthUser user) {
		inquiryService.unblind(request.inqrSn(), user);
		return ApiResponse.ok();
	}

	// ------------------------------------------------------------------

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
