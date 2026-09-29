package egovframework.eeum.file.service.file;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.file.service.file.storage.StoredResource;
import egovframework.eeum.file.vo.AtchFileVO;
import egovframework.eeum.file.web.dto.FileDetailResponse;
import egovframework.eeum.file.web.dto.FileUploadResponse;

/**
 * 파일첨부 서비스 — Phase 1(공개 이미지). 업로드는 인증 필요, 조회·다운로드는 공개.
 */
public interface FileService {

	/** 공개 이미지 업로드 — 검증(확장자·크기·매직바이트) 후 SFTP 저장 + 메타 등록. */
	FileUploadResponse upload(MultipartFile file, long uploaderUserSn, String trgtType, String trgtId);

	/**
	 * 스코프별 정책으로 이미지를 저장 — 검증(확장자·크기·매직바이트·픽셀·쿼터) 후 SFTP 저장 + 메타(LINKED) 등록.
	 * 공개 이미지 {@link #upload}가 이 메서드를 재사용하며, 프로필 등 다른 공개 스코프도 정책만 바꿔 호출한다.
	 * 재인코딩(EXIF 제거)이 필요하면 호출 측에서 선처리한 파일을 넘긴다.
	 */
	FileUploadResponse storeImage(MultipartFile file, long uploaderUserSn, String trgtType, String trgtId,
			ImageStorePolicy storePolicy);

	/**
	 * 임시(TEMP) 이미지를 저장 — 검증(확장자·크기·매직바이트·픽셀·쿼터) 후 SFTP 저장 + 메타(TEMP, 미연결) 등록.
	 * 선업로드-후연결 흐름(문의 첨부 등)에서 사용: 도메인 연결은 나중에 별도로 수행한다.
	 * 재인코딩(EXIF 제거)이 필요하면 호출 측에서 선처리한 파일을 넘긴다.
	 *
	 * @param atchVisbltyCd 희망 공개성(PUBLIC/PRIVATE) — 실제 확정은 연결 시점(글 공개성이 상한선)
	 * @param ttlHours      미연결 정리 TTL(시간) — TEMP_EXPIRE_DT=현재+ttlHours
	 */
	FileUploadResponse storeTempImage(MultipartFile file, long uploaderUserSn, ImageStorePolicy storePolicy,
			String atchVisbltyCd, int ttlHours);

	/** 메타 조회(공개 이미지). */
	FileDetailResponse detail(long atchFileSn);

	/** 다운로드 메타 조회(공개 이미지) — 스트림은 열지 않음(헤더용 메타 + 저장키). */
	FileDownload download(long atchFileSn);

	/** 저장키로 스트림 지연 개방 — 컨트롤러가 StreamingResponseBody 내부에서 호출·close(세션 누수 방지). */
	StoredResource openStream(String strgKey);

	/** 삭제(soft) — 등록자 본인 또는 운영자만. */
	void delete(long atchFileSn, AuthUser actor);

	/**
	 * 첨부 1건 soft delete + 물리삭제(best-effort) — 도메인 소유 서비스가 권한을 확인한 뒤 호출(권한 재검증 안 함).
	 * 프로필 이미지 교체 시 이전 파일 정리 등에 사용. 메타가 없거나 이미 삭제됐으면 조용히 무시(멱등).
	 */
	void softDeleteByAtchFileSn(long atchFileSn, long actorUserSn);

	// ---- 선업로드-후연결(TEMP/LINKED) 첨부 — 도메인 서비스(문의 등)가 서비스 경유로 접근(매퍼 직접접근 금지, ArchUnit) ----

	/** 연결 검증용 1건 조회(FOR UPDATE) — 도메인 서비스가 6중검증 입력으로 사용. */
	AtchFileVO selectForLink(long atchFileSn);

	/** TEMP→LINKED 전이 + 대상/연결일시/공개성 확정. 조건을 WHERE 로 재강제(경합 방어), 0건이면 검증 실패. */
	int linkAttachment(long atchFileSn, long inqrSn, String atchVisbltyCd, long actorUserSn);

	/**
	 * 문의 첨부 1건 연결해제 — 해당 문의에 LINKED 된 INQR_ATTACH 1건을 soft delete + 물리삭제(best-effort).
	 * 공개스코프용 {@link #softDeleteByAtchFileSn} 가 INQR_ATTACH 를 못 잡는 문제를 분리 처리(권한은 호출 측이 확인).
	 */
	void unlinkInquiryAttachment(long atchFileSn, long inqrSn, long actorUserSn);

	/**
	 * 만료 TEMP 1건 정리 — 미연결·만료 조건 재강제 soft delete 후 0건이 아니면 물리삭제(best-effort).
	 * 정리잡이 읽은 뒤 그새 LINKED/삭제되면 0건이라 삭제하지 않는다(TTL 경합 방어). 영향 건수 반환.
	 */
	int cleanupExpiredTempAttachment(long atchFileSn);

	/** 단건 첨부 공개성 변경(공개글 수정 시 기존 첨부 PUBLIC↔PRIVATE 반영) — INQR_ATTACH+LINKED 한정. 영향 건수 반환. */
	int updateAttachmentVisibility(long atchFileSn, String visibility, long actorUserSn);

	/** 문의에 연결된 활성 첨부 목록(상세/수정 판정용). */
	List<AtchFileVO> selectAttachmentsByInquiry(long inqrSn);

	/** 문의 공개→비밀 전환 보정 — 연결 첨부를 전부 PRIVATE 강제(전이 규칙). 영향 건수 반환. */
	int updateVisibilityByInquiry(long inqrSn, String atchVisbltyCd, long actorUserSn);

	/** 다운로드 권한판정용 — 문의 첨부 1건 조회(공개성/소속글 raw 메타, 권한분기는 호출 측). */
	AtchFileVO selectInquiryAttachmentForDownload(long atchFileSn);

	/** 만료된 미연결 임시(TEMP) 첨부 목록 — TTL 정리잡 대상. */
	List<AtchFileVO> selectExpiredTempAttachments(int limit);
}
