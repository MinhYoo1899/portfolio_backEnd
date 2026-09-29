package egovframework.eeum.file.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 첨부파일 메타데이터 VO — ATCH_FILE 매핑.
 *
 * <p>물리 파일은 SFTP 저장소에, 본 VO 는 메타만 보관(다형 대상 TRGT_TYPE/TRGT_ID 는 논리참조).
 * MyBatis 매핑을 위해 기본생성자+세터 유지.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtchFileVO {

	/** 첨부파일일련번호(PK). */
	private Long atchFileSn;

	/** 파일공개범위코드(PUBLIC_IMAGE/PRIVATE_DOC/INQR_ATTACH 등 — 용도/검증정책). */
	private String fileScopeCd;

	/** 첨부공개성코드(PUBLIC=전체 열람/PRIVATE=작성자+관리권한 운영자만) — 스코프(용도)와 분리된 접근권한. */
	private String atchVisbltyCd;

	/** 대상유형(다형 논리참조). */
	private String trgtType;

	/** 대상식별자. */
	private String trgtId;

	/** 원본파일명. */
	private String orgnlFileNm;

	/** 저장파일명(UUID 기반). */
	private String strgFileNm;

	/** 저장소유형코드(LOCAL/OBJECT_STORAGE/SFTP). */
	private String strgTypeCd;

	/** 저장소키(환경독립 상대경로 — base 경로 미포함). */
	private String strgKey;

	/** 공개URL주소(CDN — 백엔드 스트리밍이면 null). */
	private String urlAddr;

	/** MIME유형명. */
	private String mimeTypeNm;

	/** 확장자명. */
	private String extnNm;

	/** 파일크기(byte). */
	private Long fileSz;

	/** 보안검사상태코드(NOT_REQUIRED/PENDING/PASSED/FAILED — AV 미연동 시 NOT_REQUIRED). */
	private String scanSttsCd;

	/** 보안검사일시. */
	private LocalDateTime scanDt;

	/** 첨부연결상태코드(TEMP=업로드만/LINKED=도메인 연결) — soft delete(DEL_YN)와 분리. */
	private String atchSttsCd;

	/** 임시파일 만료일시(TEMP 선업로드 TTL — 미연결 정리 기준). */
	private LocalDateTime tempExpireDt;

	/** 도메인 연결 완료 일시. */
	private LocalDateTime linkedDt;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자(사용자일련번호). */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자(사용자일련번호). */
	private Long mdfrSn;

	/** 삭제여부. */
	private String delYn;
}
