package egovframework.eeum.file.service.file.impl;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.audit.Audit;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.FileDownload;
import egovframework.eeum.file.service.file.FilePolicyProperties;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.ImageContentValidator;
import egovframework.eeum.file.service.file.ImageStorePolicy;
import egovframework.eeum.file.service.file.dao.FileMapper;
import egovframework.eeum.file.service.file.storage.FileStorage;
import egovframework.eeum.file.service.file.storage.StoredResource;
import egovframework.eeum.file.vo.AtchFileVO;
import egovframework.eeum.file.vo.UploadUsageVO;
import egovframework.eeum.file.web.dto.FileDetailResponse;
import egovframework.eeum.file.web.dto.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 파일첨부 서비스 구현 — Phase 1(공개 이미지, SFTP 저장).
 *
 * <p>업로드 정합(codex 권고): 저장(임시→rename) 후 메타 INSERT, INSERT 실패 시 저장파일 보상 삭제.
 * 다운로드는 스코프(PUBLIC_IMAGE)+미삭제만 노출(IDOR 방어).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

	private static final String SCOPE_PUBLIC_IMAGE = "PUBLIC_IMAGE";
	private static final String USER_TYPE_OPERATOR = "OPERATOR";
	/** AV(바이러스검사) 미연동 — 검사면제를 NULL 대신 명시(SCAN_STTS_CD). */
	private static final String SCAN_NOT_REQUIRED = "NOT_REQUIRED";
	private static final int MAX_ORGNL_FILE_NM = 300;
	private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

	private final FileMapper fileMapper;
	private final FileStorage fileStorage;
	private final FilePolicyProperties policy;
	private final ImageContentValidator imageValidator;

	/** 업로더별 직렬화용 락 스트라이프(고정 개수 — userSn 무한 축적 없이 동일 업로더 쿼터 TOCTOU 완화). */
	private static final int UPLOAD_LOCK_STRIPES = 64;
	private final Object[] uploadLocks = newLockStripes();

	private static Object[] newLockStripes() {
		Object[] locks = new Object[UPLOAD_LOCK_STRIPES];
		for (int i = 0; i < locks.length; i++) {
			locks[i] = new Object();
		}
		return locks;
	}

	private Object uploadLock(long userSn) {
		return uploadLocks[(int) (Long.hashCode(userSn) & 0x7fffffff) % uploadLocks.length];
	}

	@Override
	@Audit(actn = "FILE_UPLOAD", trgtType = "ATCH_FILE", trgtId = "#result.atchFileSn()")
	public FileUploadResponse upload(MultipartFile file, long uploaderUserSn, String trgtType, String trgtId) {
		return storeImage(file, uploaderUserSn, trgtType, trgtId, publicImagePolicy());
	}

	@Override
	public FileUploadResponse storeImage(MultipartFile file, long uploaderUserSn, String trgtType, String trgtId,
			ImageStorePolicy storePolicy) {
		// 동일 업로더 직렬화 — 쿼터 SELECT→INSERT 사이 TOCTOU 완화(단일 인스턴스 기준).
		// 다중 인스턴스로 확장하면 DB 카운터/락으로 대체해야 함(백로그).
		synchronized (uploadLock(uploaderUserSn)) {
			return doStore(file, uploaderUserSn, trgtType, trgtId, storePolicy);
		}
	}

	@Override
	public FileUploadResponse storeTempImage(MultipartFile file, long uploaderUserSn, ImageStorePolicy storePolicy,
			String atchVisbltyCd, int ttlHours) {
		synchronized (uploadLock(uploaderUserSn)) {
			return doStoreTemp(file, uploaderUserSn, storePolicy, atchVisbltyCd, ttlHours);
		}
	}

	/** 공개 이미지 정책 — FilePolicyProperties(eeum.file.public-image.*)에서 파생. */
	private ImageStorePolicy publicImagePolicy() {
		return new ImageStorePolicy(SCOPE_PUBLIC_IMAGE, "public",
				policy.maxSizeBytes(), policy.getAllowedExtensions(), policy.getMaxPixels(),
				policy.getDailyMaxCount(), policy.dailyMaxBytes());
	}

	// @Transactional 미사용: 느린 SFTP 업로드가 DB 커넥션을 점유하지 않도록(메타 INSERT는 단일 statement 라 autocommit,
	// 실패 시 저장파일 보상삭제로 정합 유지 — codex 교차검토 Medium).
	private FileUploadResponse doStore(MultipartFile file, long uploaderUserSn, String trgtType, String trgtId,
			ImageStorePolicy storePolicy) {
		// 검증 + SFTP 저장(공통) → 즉시연결(LINKED) 메타 등록
		AtchFileVO vo = validateAndStore(file, uploaderUserSn, storePolicy);
		vo.setTrgtType(trgtType);
		vo.setTrgtId(trgtId);
		try {
			fileMapper.insertAtchFile(vo);
		} catch (RuntimeException e) {
			compensate(vo.getStrgKey());
			throw e;
		}
		return FileUploadResponse.from(vo, downloadUrl(vo.getAtchFileSn()));
	}

	// 선업로드(TEMP) — 검증 + SFTP 저장(공통) 후 미연결 TEMP 메타 등록(희망 공개성 보존, TTL 설정).
	private FileUploadResponse doStoreTemp(MultipartFile file, long uploaderUserSn, ImageStorePolicy storePolicy,
			String atchVisbltyCd, int ttlHours) {
		AtchFileVO vo = validateAndStore(file, uploaderUserSn, storePolicy);
		vo.setAtchVisbltyCd(atchVisbltyCd);
		try {
			fileMapper.insertTempAttachment(vo, ttlHours);
		} catch (RuntimeException e) {
			compensate(vo.getStrgKey());
			throw e;
		}
		return FileUploadResponse.from(vo, downloadUrl(vo.getAtchFileSn()));
	}

	/**
	 * 공통 검증(저장소 가용/빈파일/크기/쿼터/확장자/매직바이트/픽셀) + SFTP 저장 후 메타 VO 구성(insert 전).
	 * 연결 상태(LINKED/TEMP)·대상·공개성은 호출 측에서 채운다.
	 */
	private AtchFileVO validateAndStore(MultipartFile file, long uploaderUserSn, ImageStorePolicy storePolicy) {
		if (!fileStorage.isAvailable()) {
			throw new BusinessException(FileErrorCode.FILE_STORAGE_UNAVAILABLE);
		}
		if (file == null || file.isEmpty()) {
			throw new BusinessException(FileErrorCode.FILE_EMPTY);
		}
		long size = file.getSize();
		if (size > storePolicy.maxSizeBytes()) {
			throw new BusinessException(FileErrorCode.FILE_TOO_LARGE);
		}
		// 업로드 쿼터(최근 24시간 건수·용량) — 저장소 고갈 방어
		enforceUploadQuota(uploaderUserSn, size, storePolicy);
		String orgnl = sanitizeFilename(file.getOriginalFilename());
		String ext = extensionOf(orgnl);
		if (!storePolicy.isAllowedExtension(ext)) {
			throw new BusinessException(FileErrorCode.FILE_EXTENSION_NOT_ALLOWED);
		}
		// 매직바이트 — 확장자 위조 차단(선언 MIME 불신)
		if (!imageValidator.matches(ext, readHeader(file))) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}
		// 해상도 상한 — 픽셀폭탄/디코딩 자원고갈 방어(디코딩 없이 헤더만)
		enforcePixelLimit(file, storePolicy);
		String mime = imageValidator.mimeOf(ext);

		String strgFileNm = UUID.randomUUID().toString().replace("-", "") + "." + ext;
		String relativeKey = storePolicy.storageKeyPrefix() + "/" + LocalDate.now().format(YYYYMM) + "/" + strgFileNm;

		// 저장(임시 업로드 → 원자적 rename)
		try (InputStream in = file.getInputStream()) {
			fileStorage.store(relativeKey, in);
		} catch (IOException e) {
			throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR);
		}

		return AtchFileVO.builder()
				.fileScopeCd(storePolicy.fileScopeCd())
				.orgnlFileNm(orgnl)
				.strgFileNm(strgFileNm)
				.strgTypeCd(fileStorage.storageTypeCode())
				.strgKey(relativeKey)
				.mimeTypeNm(mime)
				.extnNm(ext)
				.fileSz(size)
				.scanSttsCd(SCAN_NOT_REQUIRED)
				.rgtrSn(uploaderUserSn)
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public FileDetailResponse detail(long atchFileSn) {
		AtchFileVO vo = requirePublicImage(atchFileSn);
		return FileDetailResponse.from(vo, downloadUrl(vo.getAtchFileSn()));
	}

	@Override
	@Transactional(readOnly = true)
	public FileDownload download(long atchFileSn) {
		AtchFileVO vo = requirePublicImage(atchFileSn);
		// 스트림은 여기서 열지 않는다 — 컨트롤러가 StreamingResponseBody 내부에서 openStream 으로 지연 개방(세션 누수 방지)
		return new FileDownload(vo.getOrgnlFileNm(), vo.getMimeTypeNm(), vo.getFileSz(), vo.getStrgKey());
	}

	@Override
	public StoredResource openStream(String strgKey) {
		return fileStorage.open(strgKey);
	}

	@Override
	@Transactional
	@Audit(actn = "FILE_DELETE", trgtType = "ATCH_FILE", trgtId = "#atchFileSn")
	public void delete(long atchFileSn, AuthUser actor) {
		// 범용 삭제는 PUBLIC_IMAGE 만 — 프로필 등 도메인 소유 파일은 이 경로로 못 지운다(참조 무결성, codex High 1)
		AtchFileVO vo = fileMapper.selectDeletablePublicImageBySn(atchFileSn);
		if (vo == null) {
			throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
		}
		boolean owner = vo.getRgtrSn() != null && vo.getRgtrSn().equals(actor.userSn());
		boolean operator = USER_TYPE_OPERATOR.equals(actor.userTypeCd());
		if (!owner && !operator) {
			throw new BusinessException(FileErrorCode.FILE_DELETE_FORBIDDEN);
		}
		int updated = fileMapper.softDeleteAtchFile(atchFileSn, actor.userSn());
		if (updated == 0) {
			throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
		}
		// 물리 삭제는 커밋 이후 best-effort — 롤백 시 파일 보존, 실패해도 soft delete 유지(고아는 별도 정리)
		deletePhysicalAfterCommit(vo.getStrgKey());
	}

	@Override
	@Transactional
	public void softDeleteByAtchFileSn(long atchFileSn, long actorUserSn) {
		// 도메인 소유 서비스(프로필 교체 등)가 권한을 이미 확인한 뒤 호출 — 여기서는 권한 재검증하지 않는다.
		AtchFileVO vo = fileMapper.selectPublicImageBySn(atchFileSn);
		if (vo == null) {
			return; // 이미 삭제됐거나 없음 — 멱등
		}
		int updated = fileMapper.softDeleteAtchFile(atchFileSn, actorUserSn);
		if (updated == 0) {
			return; // 경합으로 이미 삭제됨 — 멱등
		}
		// 물리 삭제는 커밋 이후로 미룬다 — 트랜잭션(프로필 교체 등) 롤백 시 DB 참조는 유지되는데 파일만 사라지는 것을 방지(codex Medium 3).
		// 활성 트랜잭션이 없으면(보상 정리 등 트랜잭션 밖 호출) 즉시 best-effort 삭제.
		deletePhysicalAfterCommit(vo.getStrgKey());
	}

	// ---- 선업로드-후연결 첨부 — 매퍼 직접접근 금지(ArchUnit) 위해 도메인 서비스에 서비스 경유로 노출 ----

	@Override
	public AtchFileVO selectForLink(long atchFileSn) {
		return fileMapper.selectForLink(atchFileSn);
	}

	@Override
	public int linkAttachment(long atchFileSn, long inqrSn, String atchVisbltyCd, long actorUserSn) {
		return fileMapper.linkAttachment(atchFileSn, String.valueOf(inqrSn), atchVisbltyCd, actorUserSn);
	}

	@Override
	@Transactional
	public void unlinkInquiryAttachment(long atchFileSn, long inqrSn, long actorUserSn) {
		// 물리삭제용 strgKey 확보(잠금) 후 소속 문의에 LINKED 된 INQR_ATTACH 만 조건부 soft delete.
		AtchFileVO vo = fileMapper.selectForLink(atchFileSn);
		if (vo == null) {
			return; // 없음 — 멱등
		}
		int updated = fileMapper.unlinkInquiryAttachment(atchFileSn, String.valueOf(inqrSn), actorUserSn);
		if (updated == 0) {
			return; // 경합(이미 해제/타문의 연결) — 물리삭제 안 함
		}
		deletePhysicalAfterCommit(vo.getStrgKey());
	}

	@Override
	@Transactional
	public int cleanupExpiredTempAttachment(long atchFileSn) {
		// strgKey 확보 후 미연결·만료 TEMP 조건부 soft delete — 0건이면 그새 연결/삭제(물리삭제 skip, TTL 경합 방어).
		AtchFileVO vo = fileMapper.selectForLink(atchFileSn);
		if (vo == null) {
			return 0;
		}
		int updated = fileMapper.softDeleteExpiredTempAttachment(atchFileSn);
		if (updated > 0) {
			deletePhysicalAfterCommit(vo.getStrgKey());
		}
		return updated;
	}

	@Override
	public int updateAttachmentVisibility(long atchFileSn, String visibility, long actorUserSn) {
		return fileMapper.updateAttachmentVisibility(atchFileSn, visibility, actorUserSn);
	}

	@Override
	public List<AtchFileVO> selectAttachmentsByInquiry(long inqrSn) {
		return fileMapper.selectAttachmentsByInquiry(String.valueOf(inqrSn));
	}

	@Override
	public int updateVisibilityByInquiry(long inqrSn, String atchVisbltyCd, long actorUserSn) {
		return fileMapper.updateVisibilityByInquiry(String.valueOf(inqrSn), atchVisbltyCd, actorUserSn);
	}

	@Override
	public AtchFileVO selectInquiryAttachmentForDownload(long atchFileSn) {
		return fileMapper.selectInquiryAttachmentForDownload(atchFileSn);
	}

	@Override
	public List<AtchFileVO> selectExpiredTempAttachments(int limit) {
		return fileMapper.selectExpiredTempAttachments(limit);
	}

	/**
	 * 물리 파일 삭제를 트랜잭션 커밋 이후로 미룬다(best-effort) — 롤백 시 파일은 보존.
	 * 활성 트랜잭션이 없으면 즉시 삭제한다(트랜잭션 밖 호출 경로 호환).
	 */
	private void deletePhysicalAfterCommit(String strgKey) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					deletePhysicalBestEffort(strgKey);
				}
			});
		} else {
			deletePhysicalBestEffort(strgKey);
		}
	}

	/** 물리 삭제 best-effort — 실패해도 soft delete 는 유지(고아는 별도 정리). */
	private void deletePhysicalBestEffort(String strgKey) {
		try {
			fileStorage.delete(strgKey);
		} catch (RuntimeException e) {
			log.warn("[FILE] 물리 삭제 실패(soft delete 완료) key={}", strgKey);
		}
	}

	// ------------------------------------------------------------------

	/** 업로드 쿼터(최근 24시간 건수·누적용량) 검증 — 저장소 고갈 방어. */
	private void enforceUploadQuota(long uploaderUserSn, long size, ImageStorePolicy storePolicy) {
		// 스코프별 집계 — 공개이미지/프로필 쿼터가 상호 잠식하지 않게(codex Medium 5)
		UploadUsageVO usage = fileMapper.selectRecentUploadUsage(uploaderUserSn, storePolicy.fileScopeCd());
		if (usage == null) {
			return;
		}
		boolean countOver = usage.getUploadCount() + 1 > storePolicy.dailyMaxCount();
		boolean sizeOver = usage.getTotalBytes() + size > storePolicy.dailyMaxBytes();
		if (countOver || sizeOver) {
			throw new BusinessException(FileErrorCode.FILE_QUOTA_EXCEEDED);
		}
	}

	/** 이미지 해상도 상한 검증 — 디코딩 없이 헤더에서 픽셀 수를 읽어 픽셀폭탄을 차단(판독 불가 시 형식 오류). */
	private void enforcePixelLimit(MultipartFile file, ImageStorePolicy storePolicy) {
		long pixels;
		try (InputStream in = file.getInputStream()) {
			pixels = imageValidator.readPixelCount(in);
		} catch (IOException e) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}
		if (pixels < 0) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}
		if (pixels > storePolicy.maxPixels()) {
			throw new BusinessException(FileErrorCode.FILE_DIMENSION_TOO_LARGE);
		}
	}

	private AtchFileVO requirePublicImage(long atchFileSn) {
		AtchFileVO vo = fileMapper.selectPublicImageBySn(atchFileSn);
		if (vo == null) {
			throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
		}
		return vo;
	}

	private void compensate(String relativeKey) {
		try {
			fileStorage.delete(relativeKey);
		} catch (RuntimeException e) {
			log.error("[FILE] 보상 삭제 실패 — 고아 파일 발생 가능 key={}", relativeKey, e);
		}
	}

	private static String downloadUrl(Long atchFileSn) {
		return "/files/download?atchFileSn=" + atchFileSn;
	}

	/** 매직바이트 검사용 선행 바이트만 읽는다(전체 메모리 적재 금지 — codex 권고). */
	private byte[] readHeader(MultipartFile file) {
		try (InputStream in = file.getInputStream()) {
			byte[] buf = new byte[ImageContentValidator.HEADER_BYTES];
			int read = in.readNBytes(buf, 0, buf.length);
			return read == buf.length ? buf : Arrays.copyOf(buf, read);
		} catch (IOException e) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}
	}

	/** 표시용 원본파일명 정리 — 경로 구분자 제거(경로조작 방지), 제어문자 제거, 길이 제한. */
	private static String sanitizeFilename(String name) {
		if (name == null || name.isBlank()) {
			return "unnamed";
		}
		String base = name.replace("\\", "/");
		int slash = base.lastIndexOf('/');
		if (slash >= 0) {
			base = base.substring(slash + 1);
		}
		base = base.replaceAll("[\\r\\n\\t]", "").trim();
		if (base.isEmpty()) {
			base = "unnamed";
		}
		return base.length() > MAX_ORGNL_FILE_NM ? base.substring(0, MAX_ORGNL_FILE_NM) : base;
	}

	private static String extensionOf(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) {
			return "";
		}
		return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
