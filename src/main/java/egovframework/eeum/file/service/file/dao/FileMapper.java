package egovframework.eeum.file.service.file.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.eeum.file.vo.AtchFileVO;
import egovframework.eeum.file.vo.UploadUsageVO;

/**
 * 첨부파일 매퍼(ATCH_FILE).
 */
@Mapper
public interface FileMapper {

	/** 메타 등록 — 생성키 atchFileSn 반환. */
	void insertAtchFile(AtchFileVO atchFile);

	/**
	 * 업로더 본인의 최근 24시간 업로드 사용량(건수·누적용량) — 쿼터 검증용.
	 * 스코프(FILE_SCOPE_CD)별로 집계하여 공개이미지/프로필 쿼터가 상호 잠식하지 않게 한다.
	 */
	UploadUsageVO selectRecentUploadUsage(@Param("rgtrSn") long rgtrSn, @Param("fileScopeCd") String fileScopeCd);

	/**
	 * 공개 다운로드/상세용 1건 조회 — 공개 스코프 allowlist(PUBLIC_IMAGE/PROFILE_IMAGE/REVIEW_IMAGE)
	 * + LINKED + DEL_YN='N' 강제(스코프/IDOR 방어). 공개 노출(다운로드/상세) 전용.
	 */
	AtchFileVO selectPublicImageBySn(@Param("atchFileSn") long atchFileSn);

	/**
	 * 범용 삭제(/files/delete)용 1건 조회 — PUBLIC_IMAGE 스코프만 허용(+LINKED+DEL_YN='N').
	 * 프로필(PROFILE_IMAGE) 등 도메인 소유 파일은 범용 삭제 경로로 지울 수 없게 막아 참조 무결성을 보호한다.
	 */
	AtchFileVO selectDeletablePublicImageBySn(@Param("atchFileSn") long atchFileSn);

	/** soft delete — 0건이면 대상 없음. */
	int softDeleteAtchFile(@Param("atchFileSn") long atchFileSn, @Param("mdfrSn") long mdfrSn);

	// ---- 선업로드-후연결(TEMP/LINKED) — 문의 첨부(INQR_ATTACH) ----

	/**
	 * 임시(TEMP) 첨부 메타 등록 — ATCH_STTS_CD='TEMP', TEMP_EXPIRE_DT=현재+TTL, 희망 공개성(ATCH_VISBLTY_CD).
	 * 연결 전이므로 TRGT_TYPE/TRGT_ID 는 비운다. 생성키 atchFileSn 반환.
	 */
	void insertTempAttachment(@Param("atchFile") AtchFileVO atchFile, @Param("ttlHours") int ttlHours);

	/**
	 * 연결 검증용 1건 조회(FOR UPDATE) — 6중검증 입력. 소유자/스코프/상태/만료/대상 등 raw 메타를 잠금과 함께 가져온다.
	 * (검증 로직은 서비스에서 — 거부 사유 열거 방지 위해 단일 에러로 변환)
	 */
	AtchFileVO selectForLink(@Param("atchFileSn") long atchFileSn);

	/**
	 * TEMP → LINKED 전이 + 대상/연결일시/공개성 확정. 조건(소유자/스코프/TEMP/미삭제/미연결)을 WHERE 로 한 번 더 강제(경합 방어).
	 * 0건이면 검증 실패로 간주. 공개성은 연결 시점에 확정(비밀글이면 강제 PRIVATE).
	 * TRGT_ID 는 VARCHAR — trgtId 는 String(String.valueOf(inqrSn))로 전달.
	 */
	int linkAttachment(@Param("atchFileSn") long atchFileSn, @Param("trgtId") String trgtId,
			@Param("atchVisbltyCd") String atchVisbltyCd, @Param("mdfrSn") long mdfrSn);

	/**
	 * 문의 첨부 1건 연결해제(soft delete) — 해당 문의에 LINKED 된 INQR_ATTACH 1건만(scope/연결/소속 WHERE 강제).
	 * 0건이면 경합(이미 해제/타문의 연결) — 물리삭제 skip 판단에 사용. TRGT_ID 는 VARCHAR(String 전달).
	 */
	int unlinkInquiryAttachment(@Param("atchFileSn") long atchFileSn, @Param("trgtId") String trgtId,
			@Param("actorUserSn") long actorUserSn);

	/**
	 * 만료 TEMP 전용 조건부 soft delete — 미연결·만료 조건을 WHERE 로 재강제. 정리잡이 읽은 뒤 그새 연결/삭제되면 0건.
	 * 0건이면 물리삭제도 skip(TTL 경합 방어).
	 */
	int softDeleteExpiredTempAttachment(@Param("atchFileSn") long atchFileSn);

	/**
	 * 단건 첨부 공개성 변경(공개글 수정 시 기존 첨부 PUBLIC↔PRIVATE 반영) — INQR_ATTACH+LINKED 한정. 영향 건수 반환.
	 */
	int updateAttachmentVisibility(@Param("atchFileSn") long atchFileSn, @Param("visibility") String visibility,
			@Param("actorUserSn") long actorUserSn);

	/** 문의에 연결된 활성 첨부 목록(소속글 기준) — 상세 응답/수정 시 유지·해제 판정용. TRGT_ID 는 VARCHAR(String 전달). */
	List<AtchFileVO> selectAttachmentsByInquiry(@Param("trgtId") String trgtId);

	/**
	 * 문의 공개→비밀 전환 보정 — 해당 문의에 연결된 활성 첨부를 전부 PRIVATE 강제(전이 규칙). 영향 건수 반환.
	 * TRGT_ID 는 VARCHAR(String 전달).
	 */
	int updateVisibilityByInquiry(@Param("trgtId") String trgtId, @Param("atchVisbltyCd") String atchVisbltyCd,
			@Param("mdfrSn") long mdfrSn);

	/**
	 * 다운로드 권한판정용 — 문의 첨부 1건 조회(INQR 연결·미삭제). 공개성/소유글/소속 확인용 raw 메타.
	 * (권한 분기는 서비스 — PUBLIC=누구나, PRIVATE=작성자·관리권한 운영자만)
	 */
	AtchFileVO selectInquiryAttachmentForDownload(@Param("atchFileSn") long atchFileSn);

	/**
	 * 만료된 미연결 임시(TEMP) 첨부 목록 — TTL 정리잡이 soft delete + 물리삭제 대상으로 사용.
	 * 삭제 SQL(softDeleteExpiredTempAttachment)이 INQR_ATTACH 스코프만 지우므로 조회도 같은 스코프로 한정(다른 TEMP 스코프 추가 시 동일 행 반복 방지, codex Low 5).
	 */
	List<AtchFileVO> selectExpiredTempAttachments(@Param("limit") int limit);
}
