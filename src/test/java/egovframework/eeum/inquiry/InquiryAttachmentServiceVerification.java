package egovframework.eeum.inquiry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.FileDownload;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.ImageContentValidator;
import egovframework.eeum.file.service.file.ImageReencoder;
import egovframework.eeum.file.vo.AtchFileVO;
import egovframework.eeum.inquiry.exception.InquiryErrorCode;
import egovframework.eeum.inquiry.service.inquiry.InqrAttachPolicyProperties;
import egovframework.eeum.inquiry.service.inquiry.dao.InquiryMapper;
import egovframework.eeum.inquiry.service.inquiry.impl.InquiryAttachmentServiceImpl;
import egovframework.eeum.inquiry.vo.InqrInfoVO;
import egovframework.eeum.inquiry.web.dto.InqrAttachmentRef;

/**
 * 문의 첨부 서비스 검증 — 6중검증 / 하이브리드 공개성 / 다운로드 권한 / 개수·용량 초과(DB·SFTP 없이 mock).
 */
class InquiryAttachmentServiceVerification {

	private final FileService fileService = mock(FileService.class);
	private final InquiryMapper inquiryMapper = mock(InquiryMapper.class);
	private final ImageReencoder imageReencoder = mock(ImageReencoder.class);
	private final ImageContentValidator imageValidator = mock(ImageContentValidator.class);
	private final InqrAttachPolicyProperties policy = newPolicy();

	private final InquiryAttachmentServiceImpl service = new InquiryAttachmentServiceImpl(
			fileService, inquiryMapper, imageReencoder, imageValidator, policy);

	private static InqrAttachPolicyProperties newPolicy() {
		InqrAttachPolicyProperties p = new InqrAttachPolicyProperties();
		p.setMaxPerInquiry(5);
		p.setMaxTotalSizePerInquiry(DataSize.ofMegabytes(30));
		return p;
	}

	private static AuthUser member(long sn) {
		return new AuthUser(sn, "u@eeum.kr", "홍길동", "MEMBER", "ACTIVE", List.of("CHNL_LOGIN_USER"));
	}

	private static AuthUser operator(long sn) {
		return new AuthUser(sn, "op@eeum.kr", "운영자", "OPERATOR", "ACTIVE", List.of("INQR_ANS"));
	}

	/** 연결 가능한 정상 TEMP 첨부(소유자 ownerSn, scope INQR_ATTACH, 미연결, 만료 전). */
	private static AtchFileVO temp(long sn, long ownerSn) {
		return AtchFileVO.builder().atchFileSn(sn).fileScopeCd("INQR_ATTACH").atchSttsCd("TEMP")
				.delYn("N").rgtrSn(ownerSn).fileSz(1024L)
				.tempExpireDt(LocalDateTime.now(ZoneOffset.UTC).plusHours(12)).build();
	}

	private static AtchFileVO linkedAttachment(long sn, long inqrSn, String visibility) {
		return AtchFileVO.builder().atchFileSn(sn).fileScopeCd("INQR_ATTACH").atchVisbltyCd(visibility)
				.atchSttsCd("LINKED").trgtType("INQR").trgtId(String.valueOf(inqrSn)).delYn("N")
				.orgnlFileNm("a.png").mimeTypeNm("image/png").fileSz(1024L).strgKey("inquiry/x.png").build();
	}

	private static InqrAttachmentRef ref(long sn, String visibility) {
		return new InqrAttachmentRef(sn, visibility);
	}

	// ===== 6중검증 =====

	@Test
	@DisplayName("타인 파일 연결 거부 — FILE_ATTACHMENT_INVALID, link 호출 안 함")
	void linkRejectsOtherOwnersFile() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		when(fileService.selectForLink(10L)).thenReturn(temp(10L, 999L)); // 소유자 999 ≠ 본인 7
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.syncAttachments(1L, List.of(ref(10L, "PUBLIC")), false, member(7)));
		assertEquals(FileErrorCode.FILE_ATTACHMENT_INVALID, e.getErrorCode());
		verify(fileService, never()).linkAttachment(anyLong(), anyLong(), any(), anyLong());
	}

	@Test
	@DisplayName("이미 타 문의에 연결된 파일 거부 — TRGT 존재 시 INVALID")
	void linkRejectsAlreadyLinked() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		AtchFileVO already = temp(10L, 7L);
		already.setTrgtType("INQR");
		already.setTrgtId("99"); // 타 문의 연결됨
		when(fileService.selectForLink(10L)).thenReturn(already);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.syncAttachments(1L, List.of(ref(10L, "PUBLIC")), false, member(7)));
		assertEquals(FileErrorCode.FILE_ATTACHMENT_INVALID, e.getErrorCode());
		verify(fileService, never()).linkAttachment(anyLong(), anyLong(), any(), anyLong());
	}

	@Test
	@DisplayName("만료된 TEMP 연결 거부 — INVALID")
	void linkRejectsExpired() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		AtchFileVO expired = temp(10L, 7L);
		expired.setTempExpireDt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1)); // 만료
		when(fileService.selectForLink(10L)).thenReturn(expired);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.syncAttachments(1L, List.of(ref(10L, "PUBLIC")), false, member(7)));
		assertEquals(FileErrorCode.FILE_ATTACHMENT_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("잘못된 scope(공개이미지 등) 연결 거부 — INVALID")
	void linkRejectsWrongScope() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		AtchFileVO wrong = temp(10L, 7L);
		wrong.setFileScopeCd("PUBLIC_IMAGE");
		when(fileService.selectForLink(10L)).thenReturn(wrong);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.syncAttachments(1L, List.of(ref(10L, "PUBLIC")), false, member(7)));
		assertEquals(FileErrorCode.FILE_ATTACHMENT_INVALID, e.getErrorCode());
	}

	// ===== 하이브리드 공개성 =====

	@Test
	@DisplayName("공개글 + 비공개 첨부 허용 — 희망 PRIVATE 그대로 연결")
	void publicInquiryAllowsPrivateAttachment() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		when(fileService.selectForLink(10L)).thenReturn(temp(10L, 7L));
		when(fileService.linkAttachment(eq(10L), eq(1L), eq("PRIVATE"), eq(7L))).thenReturn(1);
		service.syncAttachments(1L, List.of(ref(10L, "PRIVATE")), false, member(7));
		verify(fileService).linkAttachment(10L, 1L, "PRIVATE", 7L);
	}

	@Test
	@DisplayName("비밀글 + 공개(PUBLIC) 희망 첨부 — PRIVATE 로 강제 연결(공개 차단)")
	void secretInquiryForcesPrivate() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		when(fileService.selectForLink(10L)).thenReturn(temp(10L, 7L));
		when(fileService.linkAttachment(eq(10L), eq(1L), eq("PRIVATE"), eq(7L))).thenReturn(1);
		service.syncAttachments(1L, List.of(ref(10L, "PUBLIC")), true, member(7));
		verify(fileService).linkAttachment(10L, 1L, "PRIVATE", 7L); // PUBLIC 희망이어도 PRIVATE 강제
		verify(fileService).updateVisibilityByInquiry(1L, "PRIVATE", 7L); // 기존 유지분도 보정
	}

	@Test
	@DisplayName("공개→비밀 전환 — 그 글 첨부 전부 PRIVATE 보정(전이 규칙)")
	void secretTransitionForcesExistingPrivate() {
		// 수정 시 기존 첨부 그대로 유지 + 비밀 전환
		AtchFileVO kept = linkedAttachment(10L, 1L, "PUBLIC");
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of(kept));
		service.syncAttachments(1L, List.of(ref(10L, "PUBLIC")), true, member(7));
		// 기존 연결은 link 재호출 없이 updateVisibilityByInquiry 로 PRIVATE 전이
		verify(fileService, never()).linkAttachment(anyLong(), anyLong(), any(), anyLong());
		verify(fileService).updateVisibilityByInquiry(1L, "PRIVATE", 7L);
	}

	@Test
	@DisplayName("요청에서 빠진 기존 첨부 — 연결해제는 unlinkInquiryAttachment 사용(INQR_ATTACH 실제 삭제, codex High 1)")
	void removedAttachmentUsesInquiryUnlink() {
		AtchFileVO kept = linkedAttachment(10L, 1L, "PUBLIC");
		AtchFileVO dropped = linkedAttachment(11L, 1L, "PUBLIC");
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of(kept, dropped));
		// 요청은 10 만 — 11 은 빠짐 → 해제
		service.syncAttachments(1L, List.of(ref(10L, null)), false, member(7));
		verify(fileService).unlinkInquiryAttachment(11L, 1L, 7L);
		verify(fileService, never()).unlinkInquiryAttachment(eq(10L), anyLong(), anyLong());
		// 공개스코프용(프로필 등) 삭제 경로는 INQR_ATTACH 해제에 사용하지 않는다
		verify(fileService, never()).softDeleteByAtchFileSn(anyLong(), anyLong());
	}

	// TTL 정리 테스트는 파일첨부 도메인으로 이동(FileAttachmentCleanupJobVerification) — 정리 책임 통합.

	@Test
	@DisplayName("공개글 수정 — 기존 첨부 PUBLIC→PRIVATE 명시 변경 반영(codex Med 3)")
	void publicInquiryUpdateAppliesExistingVisibilityChange() {
		AtchFileVO kept = linkedAttachment(10L, 1L, "PUBLIC");
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of(kept));
		service.syncAttachments(1L, List.of(ref(10L, "PRIVATE")), false, member(7));
		verify(fileService).updateAttachmentVisibility(10L, "PRIVATE", 7L);
		verify(fileService, never()).linkAttachment(anyLong(), anyLong(), any(), anyLong());
	}

	@Test
	@DisplayName("공개글 수정 — visibility 미지정(null)이면 기존 공개성 유지(자동 공개 방지, codex Med 3)")
	void publicInquiryUpdateKeepsVisibilityWhenUnspecified() {
		AtchFileVO kept = linkedAttachment(10L, 1L, "PRIVATE");
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of(kept));
		service.syncAttachments(1L, List.of(ref(10L, null)), false, member(7));
		verify(fileService, never()).updateAttachmentVisibility(anyLong(), any(), anyLong());
	}

	@Test
	@DisplayName("신규 연결 — visibility 생략(null)이면 TEMP 행 희망 공개성 승계(업로드 PRIVATE 의도 보존, codex Med 3)")
	void newLinkInheritsTempVisibilityWhenUnspecified() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		AtchFileVO temp = temp(10L, 7L);
		temp.setAtchVisbltyCd("PRIVATE"); // 업로드 때 PRIVATE 로 보낸 의도
		when(fileService.selectForLink(10L)).thenReturn(temp);
		when(fileService.linkAttachment(eq(10L), eq(1L), eq("PRIVATE"), eq(7L))).thenReturn(1);
		// 연결 요청에서 visibility 생략(null) — 공개글이라도 TEMP 희망값 PRIVATE 승계
		service.syncAttachments(1L, List.of(ref(10L, null)), false, member(7));
		verify(fileService).linkAttachment(10L, 1L, "PRIVATE", 7L);
	}

	@Test
	@DisplayName("신규 연결 — visibility 명시되면 TEMP 희망값 무시하고 명시값 적용(공개글)")
	void newLinkUsesExplicitVisibilityOverTemp() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		AtchFileVO temp = temp(10L, 7L);
		temp.setAtchVisbltyCd("PRIVATE");
		when(fileService.selectForLink(10L)).thenReturn(temp);
		when(fileService.linkAttachment(eq(10L), eq(1L), eq("PUBLIC"), eq(7L))).thenReturn(1);
		service.syncAttachments(1L, List.of(ref(10L, "PUBLIC")), false, member(7));
		verify(fileService).linkAttachment(10L, 1L, "PUBLIC", 7L);
	}

	// ===== 문의 삭제 시 첨부 정리(codex Med 2) =====

	@Test
	@DisplayName("문의 삭제 — 그 문의 LINKED 첨부 전부 연결해제(unlinkInquiryAttachment)")
	void detachAllByInquiryUnlinksEvery() {
		AtchFileVO a = linkedAttachment(10L, 1L, "PUBLIC");
		AtchFileVO b = linkedAttachment(11L, 1L, "PRIVATE");
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of(a, b));
		service.detachAllByInquiry(1L, member(7));
		verify(fileService).unlinkInquiryAttachment(10L, 1L, 7L);
		verify(fileService).unlinkInquiryAttachment(11L, 1L, 7L);
		// 공개스코프용 삭제 경로는 사용하지 않음(INQR_ATTACH 전용 경로 유지)
		verify(fileService, never()).softDeleteByAtchFileSn(anyLong(), anyLong());
	}

	@Test
	@DisplayName("문의 삭제 — 첨부 없으면 연결해제 호출 없음(no-op)")
	void detachAllByInquiryNoAttachments() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		service.detachAllByInquiry(1L, member(7));
		verify(fileService, never()).unlinkInquiryAttachment(anyLong(), anyLong(), anyLong());
	}

	// ===== 개수 · 용량 초과 =====

	@Test
	@DisplayName("개수 초과(6개 > 5) — FILE_ATTACHMENT_COUNT_EXCEEDED")
	void countExceeded() {
		BusinessException e = assertThrows(BusinessException.class, () -> service.syncAttachments(
				1L, List.of(ref(1, "PUBLIC"), ref(2, "PUBLIC"), ref(3, "PUBLIC"),
						ref(4, "PUBLIC"), ref(5, "PUBLIC"), ref(6, "PUBLIC")), false, member(7)));
		assertEquals(FileErrorCode.FILE_ATTACHMENT_COUNT_EXCEEDED, e.getErrorCode());
		verify(fileService, never()).linkAttachment(anyLong(), anyLong(), any(), anyLong());
	}

	@Test
	@DisplayName("총 용량 초과(20MB+20MB > 30MB) — FILE_ATTACHMENT_SIZE_EXCEEDED")
	void totalSizeExceeded() {
		when(fileService.selectAttachmentsByInquiry(1L)).thenReturn(List.of());
		AtchFileVO big1 = temp(10L, 7L);
		big1.setFileSz(DataSize.ofMegabytes(20).toBytes());
		AtchFileVO big2 = temp(11L, 7L);
		big2.setFileSz(DataSize.ofMegabytes(20).toBytes());
		when(fileService.selectForLink(10L)).thenReturn(big1);
		when(fileService.selectForLink(11L)).thenReturn(big2);
		when(fileService.linkAttachment(eq(10L), anyLong(), any(), anyLong())).thenReturn(1);
		BusinessException e = assertThrows(BusinessException.class, () -> service.syncAttachments(
				1L, List.of(ref(10L, "PUBLIC"), ref(11L, "PUBLIC")), false, member(7)));
		assertEquals(FileErrorCode.FILE_ATTACHMENT_SIZE_EXCEEDED, e.getErrorCode());
	}

	// ===== 다운로드 권한 =====

	@Test
	@DisplayName("공개글 + PUBLIC 첨부 — 비로그인 포함 누구나 다운로드")
	void publicAttachmentAnyone() {
		when(fileService.selectInquiryAttachmentForDownload(10L)).thenReturn(linkedAttachment(10L, 1L, "PUBLIC"));
		when(inquiryMapper.selectInquiryDetail(1L)).thenReturn(
				InqrInfoVO.builder().inqrSn(1L).userSn(7L).prvtPstYn("N").pstgSttsCd("NORMAL").delYn("N").build());
		FileDownload d = service.downloadForViewer(10L, null);
		assertEquals("inquiry/x.png", d.strgKey());
	}

	@Test
	@DisplayName("비밀글 + PUBLIC 첨부 — 타인은 404(첨부 공개성 무관 부모 비밀 반영, codex Med 5·6)")
	void secretInquiryBlocksPublicAttachmentForOther() {
		when(fileService.selectInquiryAttachmentForDownload(10L)).thenReturn(linkedAttachment(10L, 1L, "PUBLIC"));
		when(inquiryMapper.selectInquiryDetail(1L)).thenReturn(
				InqrInfoVO.builder().inqrSn(1L).userSn(7L).prvtPstYn("Y").pstgSttsCd("NORMAL").delYn("N").build());
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.downloadForViewer(10L, member(999)));
		assertEquals(InquiryErrorCode.INQUIRY_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("삭제글 첨부 — PUBLIC 이어도 404(부모 selectInquiryDetail null)")
	void deletedInquiryBlocksAttachment() {
		when(fileService.selectInquiryAttachmentForDownload(10L)).thenReturn(linkedAttachment(10L, 1L, "PUBLIC"));
		when(inquiryMapper.selectInquiryDetail(1L)).thenReturn(null); // 삭제글은 DEL_YN='N' 필터로 null
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.downloadForViewer(10L, member(7)));
		assertEquals(InquiryErrorCode.INQUIRY_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("블라인드글 + PUBLIC 첨부 — 타인은 404, 운영자는 200")
	void blindedInquiryBlocksOtherAllowsOperator() {
		when(fileService.selectInquiryAttachmentForDownload(10L)).thenReturn(linkedAttachment(10L, 1L, "PUBLIC"));
		when(inquiryMapper.selectInquiryDetail(1L)).thenReturn(
				InqrInfoVO.builder().inqrSn(1L).userSn(7L).prvtPstYn("N").pstgSttsCd("BLINDED").delYn("N").build());
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.downloadForViewer(10L, member(999)));
		assertEquals(InquiryErrorCode.INQUIRY_NOT_FOUND, e.getErrorCode());

		FileDownload d = service.downloadForViewer(10L, operator(2)); // 운영자는 블라인드 열람 가능
		assertEquals("inquiry/x.png", d.strgKey());
	}

	@Test
	@DisplayName("PRIVATE 첨부 — 작성자 본인은 200")
	void privateAttachmentOwner() {
		when(fileService.selectInquiryAttachmentForDownload(10L)).thenReturn(linkedAttachment(10L, 1L, "PRIVATE"));
		when(inquiryMapper.selectInquiryDetail(1L)).thenReturn(
				InqrInfoVO.builder().inqrSn(1L).userSn(7L).prvtPstYn("Y").delYn("N").build());
		FileDownload d = service.downloadForViewer(10L, member(7));
		assertEquals("inquiry/x.png", d.strgKey());
	}

	@Test
	@DisplayName("PRIVATE 첨부 — 타인은 404")
	void privateAttachmentOtherForbidden() {
		when(fileService.selectInquiryAttachmentForDownload(10L)).thenReturn(linkedAttachment(10L, 1L, "PRIVATE"));
		when(inquiryMapper.selectInquiryDetail(1L)).thenReturn(
				InqrInfoVO.builder().inqrSn(1L).userSn(7L).prvtPstYn("Y").delYn("N").build());
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.downloadForViewer(10L, member(999)));
		assertEquals(InquiryErrorCode.INQUIRY_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("PRIVATE 첨부 — 관리권한 운영자는 200")
	void privateAttachmentOperator() {
		when(fileService.selectInquiryAttachmentForDownload(10L)).thenReturn(linkedAttachment(10L, 1L, "PRIVATE"));
		when(inquiryMapper.selectInquiryDetail(1L)).thenReturn(
				InqrInfoVO.builder().inqrSn(1L).userSn(7L).prvtPstYn("Y").delYn("N").build());
		FileDownload d = service.downloadForViewer(10L, operator(2));
		assertEquals("inquiry/x.png", d.strgKey());
	}

	@Test
	@DisplayName("존재하지 않는/미연결 첨부 다운로드 — 404")
	void downloadNotFound() {
		when(fileService.selectInquiryAttachmentForDownload(404L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.downloadForViewer(404L, member(7)));
		assertEquals(InquiryErrorCode.INQUIRY_NOT_FOUND, e.getErrorCode());
	}
}
