package egovframework.eeum.inquiry.service.inquiry.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.FileDownload;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.ImageContentValidator;
import egovframework.eeum.file.service.file.ImageReencoder;
import egovframework.eeum.file.service.file.ImageStorePolicy;
import egovframework.eeum.file.service.file.InMemoryMultipartFile;
import egovframework.eeum.file.vo.AtchFileVO;
import egovframework.eeum.file.web.dto.FileUploadResponse;
import egovframework.eeum.inquiry.exception.InquiryErrorCode;
import egovframework.eeum.inquiry.service.inquiry.InqrAttachPolicyProperties;
import egovframework.eeum.inquiry.service.inquiry.InquiryAttachmentService;
import egovframework.eeum.inquiry.service.inquiry.dao.InquiryMapper;
import egovframework.eeum.inquiry.vo.InqrInfoVO;
import egovframework.eeum.inquiry.web.dto.InqrAttachmentRef;
import egovframework.eeum.inquiry.web.dto.InqrAttachmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 문의 첨부(INQR_ATTACH) 구현 — 선업로드-후연결 + 하이브리드 공개성.
 *
 * <p>업로드는 EXIF/GPS 제거 재인코딩 후 TEMP 저장(공개성 희망값 보존). 연결은 6중검증 후 LINKED 전이하며 글 공개성을
 * 상한선으로 공개성을 확정한다(비밀글이면 PRIVATE 강제). 다운로드 권한은 비밀글 열람권한 판정을 재사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryAttachmentServiceImpl implements InquiryAttachmentService {

	private static final String SCOPE_INQR_ATTACH = "INQR_ATTACH";
	private static final String STORAGE_KEY_PREFIX = "inquiry";
	private static final String TRGT_TYPE_INQR = "INQR";
	private static final String VISIBILITY_PUBLIC = "PUBLIC";
	private static final String VISIBILITY_PRIVATE = "PRIVATE";
	private static final String Y = "Y";
	private static final String PSTG_BLINDED = "BLINDED";
	private static final String AUTH_ANS = "INQR_ANS";
	private static final String AUTH_MODER = "INQR_MODER";

	private final FileService fileService;
	private final InquiryMapper inquiryMapper;
	private final ImageReencoder imageReencoder;
	private final ImageContentValidator imageValidator;
	private final InqrAttachPolicyProperties policy;

	@Override
	@Audit(actn = "INQUIRY_ATTACH_UPLOAD", trgtType = "ATCH_FILE", trgtId = "#result.atchFileSn()")
	public FileUploadResponse uploadTemp(MultipartFile file, AuthUser uploader, String visibility) {
		AuthUser user = requireUser(uploader);
		// 0) 싼 검증부터 — 비싼 재인코딩(디코딩) 비용 전에 빠르게 거부(file 도메인과 동일 패턴)
		if (file == null || file.isEmpty()) {
			throw new BusinessException(FileErrorCode.FILE_EMPTY);
		}
		if (file.getSize() > policy.maxSizeBytes()) {
			throw new BusinessException(FileErrorCode.FILE_TOO_LARGE);
		}
		String originalExt = extensionOf(file.getOriginalFilename());
		if (!policy.getAllowedExtensions().contains(originalExt)) {
			throw new BusinessException(FileErrorCode.FILE_EXTENSION_NOT_ALLOWED);
		}
		if (!imageValidator.matches(originalExt, readHeader(file))) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}
		// 1) EXIF/GPS 제거 재인코딩(개인정보 보호) — 픽셀폭탄 방어를 위해 디코딩 전 해상도 상한 검사
		ImageReencoder.Result reencoded = imageReencoder.reencode(file, policy.getMaxPixels());
		String ext = ImageReencoder.normalizeExtension(reencoded.formatName());
		MultipartFile sanitized = new InMemoryMultipartFile(
				"file", "inquiry." + ext, reencoded.mimeType(), reencoded.bytes());

		// 2) TEMP 저장(scope=INQR_ATTACH 강제, 미연결). 공개성 희망값 보존 — 확정은 연결 시점.
		String wishedVisibility = normalizeVisibility(visibility);
		FileUploadResponse stored = fileService.storeTempImage(
				sanitized, user.userSn(), inqrAttachPolicy(ext), wishedVisibility, policy.getTempTtlHours());
		// 업로드 응답 downloadUrl 을 문의 첨부 전용 경로로 교체(연결 후 작동) — 공통 저장이 채운 공개경로(/files/download)는 INQR_ATTACH 미허용.
		return new FileUploadResponse(stored.atchFileSn(), stored.orgnlFileNm(), downloadUrl(stored.atchFileSn()),
				stored.mimeTypeNm(), stored.extnNm(), stored.fileSz());
	}

	@Override
	@Transactional
	public void syncAttachments(long inqrSn, List<InqrAttachmentRef> requested, boolean secret, AuthUser owner) {
		AuthUser user = requireUser(owner);
		List<InqrAttachmentRef> refs = requested == null ? List.of() : requested;

		// 요청 식별자 중복 제거(앞선 희망 공개성 유지) + 개수 상한.
		// 값은 raw(null=미지정) 보존 — 미지정/명시를 구분해야 기존 첨부 공개성 변경을 판정할 수 있다(codex Med 3).
		Map<Long, String> wished = new LinkedHashMap<>();
		for (InqrAttachmentRef ref : refs) {
			if (ref == null || ref.atchFileSn() == null) {
				throw new BusinessException(FileErrorCode.FILE_ATTACHMENT_INVALID);
			}
			wished.putIfAbsent(ref.atchFileSn(), ref.visibility());
		}
		if (wished.size() > policy.getMaxPerInquiry()) {
			throw new BusinessException(FileErrorCode.FILE_ATTACHMENT_COUNT_EXCEEDED);
		}

		// 현재 이 문의에 연결된 활성 첨부(수정 시 유지/해제 판정 기준)
		List<AtchFileVO> existing = fileService.selectAttachmentsByInquiry(inqrSn);
		Set<Long> existingSns = existing.stream().map(AtchFileVO::getAtchFileSn).collect(Collectors.toSet());
		Set<Long> requestedSns = wished.keySet();

		// 1) 요청에서 빠진 기존 LINKED → 연결해제(soft delete) — INQR_ATTACH 전용 경로(공개스코프용 아님, codex High 1)
		for (AtchFileVO cur : existing) {
			if (!requestedSns.contains(cur.getAtchFileSn())) {
				fileService.unlinkInquiryAttachment(cur.getAtchFileSn(), inqrSn, user.userSn());
			}
		}

		// 2) 신규(아직 미연결) 첨부 연결 — 6중검증 후 LINKED. 총 용량 상한은 신규+기존 합산으로 검증.
		long totalBytes = existing.stream()
				.filter(v -> requestedSns.contains(v.getAtchFileSn()))
				.mapToLong(v -> v.getFileSz() == null ? 0L : v.getFileSz())
				.sum();
		for (Map.Entry<Long, String> entry : wished.entrySet()) {
			long atchFileSn = entry.getKey();
			if (existingSns.contains(atchFileSn)) {
				// 이미 이 문의에 연결됨 — 유지. 단, 공개글이고 visibility 가 명시됐으면 그 값으로 변경 반영(PUBLIC↔PRIVATE).
				// 미지정(null)이면 기존 유지(비밀→공개 전환 시 자동 공개 방지). 비밀글이면 어차피 아래 전체 PRIVATE 보정.
				if (!secret && entry.getValue() != null) {
					fileService.updateAttachmentVisibility(
							atchFileSn, normalizeVisibility(entry.getValue()), user.userSn());
				}
				continue;
			}
			AtchFileVO temp = verifyTempForLink(atchFileSn, user.userSn());
			totalBytes += temp.getFileSz() == null ? 0L : temp.getFileSz();
			if (totalBytes > policy.maxTotalSizePerInquiryBytes()) {
				throw new BusinessException(FileErrorCode.FILE_ATTACHMENT_SIZE_EXCEEDED);
			}
			// 공개성 확정 — 비밀글이면 PRIVATE 강제. 공개글은 명시 희망값, 생략(null)이면 업로드 때 보존된 TEMP 행 희망값 승계(codex Med 3).
			String resolved = secret ? VISIBILITY_PRIVATE
					: normalizeVisibility(entry.getValue() != null ? entry.getValue() : temp.getAtchVisbltyCd());
			int linked = fileService.linkAttachment(atchFileSn, inqrSn, resolved, user.userSn());
			if (linked == 0) {
				// 경합(동시 연결/만료/삭제 등) — 검증 통과 후 전이 실패는 첨부 불가로 처리
				throw new BusinessException(FileErrorCode.FILE_ATTACHMENT_INVALID);
			}
		}

		// 3) 비밀글이면 기존 유지분도 PRIVATE 보정(전이/일관성)
		if (secret) {
			fileService.updateVisibilityByInquiry(inqrSn, VISIBILITY_PRIVATE, user.userSn());
		}
	}

	@Override
	@Transactional
	public void enforcePrivateOnSecretTransition(long inqrSn, AuthUser actor) {
		AuthUser user = requireUser(actor);
		fileService.updateVisibilityByInquiry(inqrSn, VISIBILITY_PRIVATE, user.userSn());
	}

	@Override
	@Transactional
	public void detachAllByInquiry(long inqrSn, AuthUser actor) {
		// 문의 삭제 시 LINKED 첨부 잔존 방지(codex Med 2) — 연결해제는 INQR_ATTACH 전용 경로(공개스코프용 아님, syncAttachments 와 동일).
		// 권한은 호출 측(문의 삭제)이 작성자 확인 완료. 같은 트랜잭션에서 호출.
		AuthUser user = requireUser(actor);
		for (AtchFileVO cur : fileService.selectAttachmentsByInquiry(inqrSn)) {
			fileService.unlinkInquiryAttachment(cur.getAtchFileSn(), inqrSn, user.userSn());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<InqrAttachmentResponse> attachmentsForDetail(long inqrSn) {
		List<InqrAttachmentResponse> result = new ArrayList<>();
		for (AtchFileVO vo : fileService.selectAttachmentsByInquiry(inqrSn)) {
			result.add(InqrAttachmentResponse.from(vo, downloadUrl(vo.getAtchFileSn())));
		}
		return result;
	}

	@Override
	@Transactional(readOnly = true)
	@Audit(actn = "INQUIRY_ATTACH_DOWNLOAD", trgtType = "ATCH_FILE", trgtId = "#atchFileSn")
	public FileDownload downloadForViewer(long atchFileSn, AuthUser viewer) {
		// 백로그(SHOULD): 순차 atchFileSn 직접 조회로 PUBLIC 첨부 열거 가능 — 1차 방어는 권한검사(LINKED·미삭제·공개성).
		// 추후 불투명 토큰/UUID 다운로드 키 도입 고려(이번엔 권한검사로 충분, 민감 첨부는 PRIVATE 로 보호됨).
		AtchFileVO attach = fileService.selectInquiryAttachmentForDownload(atchFileSn);
		if (attach == null) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND);
		}
		// 첨부 공개성만 보지 않고 항상 부모 문의 상태(삭제/블라인드/비밀)를 반영한다(codex Med 5·6).
		// 삭제글(selectInquiryDetail 가 DEL_YN='N' 필터로 null) → 404. 블라인드 → 운영자만(문의 상세 정책과 동일).
		// 비밀글 → 첨부 PUBLIC 이어도 열람권한 요구. 공개글 + PRIVATE 첨부 → 열람권한. 공개글 + PUBLIC 첨부 → 누구나.
		InqrInfoVO inqr = inquiryMapper.selectInquiryDetail(parseInqrSn(attach.getTrgtId()));
		if (inqr == null) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND); // 삭제/없음
		}
		boolean blinded = PSTG_BLINDED.equals(inqr.getPstgSttsCd());
		boolean secret = Y.equals(inqr.getPrvtPstYn());
		boolean privateAttach = VISIBILITY_PRIVATE.equals(attach.getAtchVisbltyCd());
		if (blinded || secret || privateAttach) {
			requireInquiryViewPermission(inqr, viewer);
		}
		// 스트림은 컨트롤러가 StreamingResponseBody 내부에서 지연 개방(세션 누수 방지) — 여기서는 메타만.
		return new FileDownload(attach.getOrgnlFileNm(), attach.getMimeTypeNm(), attach.getFileSz(), attach.getStrgKey());
	}

	// 미연결 만료 TEMP 정리는 파일첨부 도메인 책임으로 통합(FileAttachmentCleanupJob, 전 scope) — inquiry 전용 정리 제거.

	// ------------------------------------------------------------------

	/** 6중검증: ①소유자==본인 ②scope==INQR_ATTACH ③상태==TEMP ④미삭제 ⑤만료전 ⑥타문의 미연결. 실패는 단일 400(열거 방지). */
	private AtchFileVO verifyTempForLink(long atchFileSn, long ownerUserSn) {
		AtchFileVO vo = fileService.selectForLink(atchFileSn); // FOR UPDATE
		if (vo == null
				|| vo.getRgtrSn() == null || vo.getRgtrSn() != ownerUserSn          // ① 소유자
				|| !SCOPE_INQR_ATTACH.equals(vo.getFileScopeCd())                    // ② 용도
				|| !"TEMP".equals(vo.getAtchSttsCd())                                // ③ 상태
				|| Y.equals(vo.getDelYn())                                           // ④ 미삭제
				|| isExpired(vo)                                                     // ⑤ 만료전
				|| vo.getTrgtId() != null || vo.getTrgtType() != null) {             // ⑥ 타문의 미연결
			throw new BusinessException(FileErrorCode.FILE_ATTACHMENT_INVALID);
		}
		return vo;
	}

	private static boolean isExpired(AtchFileVO vo) {
		return vo.getTempExpireDt() != null
				&& vo.getTempExpireDt().isBefore(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
	}

	/** 소속 문의 열람권한(비밀글/블라인드 판정 재사용) — 작성자 본인 또는 관리권한 운영자만. 아니면 404(열거 방지). */
	private void requireInquiryViewPermission(InqrInfoVO inqr, AuthUser viewer) {
		boolean owner = viewer != null && viewer.userSn() != null && viewer.userSn().equals(inqr.getUserSn());
		boolean operator = isOperatorView(viewer);
		if (!owner && !operator) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND);
		}
	}

	private static long parseInqrSn(String trgtId) {
		try {
			return Long.parseLong(trgtId);
		} catch (NumberFormatException e) {
			throw new BusinessException(InquiryErrorCode.INQUIRY_NOT_FOUND);
		}
	}

	/** INQR 관리 권한(답변·게시관리 중 하나) 보유 = 비밀 첨부 열람 가능한 운영자(비밀글 판정과 동일). */
	private static boolean isOperatorView(AuthUser user) {
		return user != null
				&& (user.permissions().contains(AUTH_ANS) || user.permissions().contains(AUTH_MODER));
	}

	private ImageStorePolicy inqrAttachPolicy(String ext) {
		return new ImageStorePolicy(SCOPE_INQR_ATTACH, STORAGE_KEY_PREFIX,
				policy.maxSizeBytes(), List.of(ext), policy.getMaxPixels(),
				policy.getDailyMaxCount(), policy.dailyMaxBytes());
	}

	private static String normalizeVisibility(String visibility) {
		if (visibility == null || visibility.isBlank()) {
			return VISIBILITY_PUBLIC;
		}
		String v = visibility.trim().toUpperCase(Locale.ROOT);
		return VISIBILITY_PRIVATE.equals(v) ? VISIBILITY_PRIVATE : VISIBILITY_PUBLIC;
	}

	private static String downloadUrl(Long atchFileSn) {
		return "/inquiries/files/download?atchFileSn=" + atchFileSn;
	}

	private static AuthUser requireUser(AuthUser user) {
		if (user == null) {
			throw BusinessException.unauthorized();
		}
		return user;
	}

	private byte[] readHeader(MultipartFile file) {
		try (java.io.InputStream in = file.getInputStream()) {
			byte[] buf = new byte[ImageContentValidator.HEADER_BYTES];
			int read = in.readNBytes(buf, 0, buf.length);
			return read == buf.length ? buf : java.util.Arrays.copyOf(buf, read);
		} catch (java.io.IOException e) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}
	}

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
