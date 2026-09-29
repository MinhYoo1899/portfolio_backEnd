package egovframework.eeum.inquiry.service.inquiry;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.file.service.file.FileDownload;
import egovframework.eeum.file.web.dto.FileUploadResponse;
import egovframework.eeum.inquiry.web.dto.InqrAttachmentRef;
import egovframework.eeum.inquiry.web.dto.InqrAttachmentResponse;

/**
 * 문의 첨부(INQR_ATTACH) — 선업로드-후연결 + 하이브리드 공개성.
 *
 * <p>업로드(임시) → 문의 작성/수정 시 연결(6중검증) → 공개성 확정(글 공개성이 상한선). 다운로드는 공개성에 따라
 * 누구나(PUBLIC) 또는 작성자·관리권한 운영자(PRIVATE)만. 미연결 TEMP 는 TTL 정리.
 */
public interface InquiryAttachmentService {

	/**
	 * 임시 첨부 업로드 — 본인 인증, scope=INQR_ATTACH 강제, 상태 TEMP, EXIF/GPS 제거 재인코딩.
	 * 공개성 희망값은 받되 확정은 연결 시점.
	 */
	FileUploadResponse uploadTemp(MultipartFile file, AuthUser uploader, String visibility);

	/**
	 * 문의 본문 저장 후 같은 트랜잭션에서 첨부를 연결한다(6중검증 + 공개성 확정).
	 * 수정 시 기존 LINKED 중 목록에 빠진 것은 연결해제(soft delete). 비밀글이면 공개성 강제 PRIVATE.
	 *
	 * @param secret 소속 문의가 비밀글인지 — true 면 모든 첨부 PRIVATE 강제
	 */
	void syncAttachments(long inqrSn, List<InqrAttachmentRef> requested, boolean secret, AuthUser owner);

	/** 문의 공개→비밀 전환 시 그 글 첨부 전부 PRIVATE 강제(전이 규칙). */
	void enforcePrivateOnSecretTransition(long inqrSn, AuthUser actor);

	/**
	 * 문의 삭제 시 그 문의에 연결된 활성 LINKED 첨부를 전부 연결해제(soft delete + 물리삭제 best-effort).
	 * 권한은 호출 측(문의 삭제)이 작성자 확인을 마친 뒤 호출한다(여기서 재검증하지 않음).
	 */
	void detachAllByInquiry(long inqrSn, AuthUser actor);

	/** 상세 응답용 — 문의에 연결된 활성 첨부 목록(다운로드 URL 포함). */
	List<InqrAttachmentResponse> attachmentsForDetail(long inqrSn);

	/**
	 * 다운로드 권한판정 — PUBLIC 이면 누구나, PRIVATE 이면 작성자·관리권한 운영자만(아니면 404).
	 * 권한 통과 후 스트리밍용 메타(저장키 포함)를 반환한다.
	 */
	FileDownload downloadForViewer(long atchFileSn, AuthUser viewer);
}
