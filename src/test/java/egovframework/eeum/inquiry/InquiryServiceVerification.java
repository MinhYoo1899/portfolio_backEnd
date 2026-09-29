package egovframework.eeum.inquiry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.inquiry.exception.InquiryErrorCode;
import egovframework.eeum.inquiry.service.inquiry.InquiryAttachmentService;
import egovframework.eeum.inquiry.service.inquiry.dao.InquiryMapper;
import egovframework.eeum.inquiry.service.inquiry.impl.InquiryServiceImpl;
import egovframework.eeum.inquiry.vo.InqrInfoVO;
import egovframework.eeum.inquiry.web.dto.InqrBlindRequest;
import egovframework.eeum.inquiry.web.dto.InqrInsertRequest;
import egovframework.eeum.inquiry.web.dto.InqrUpdateRequest;

/**
 * 문의 서비스 검증 — 비밀글 기본값/소유권/수정잠금/상세 접근제어/블라인드(DB 없이 mock).
 */
class InquiryServiceVerification {

	private final InquiryMapper mapper = mock(InquiryMapper.class);
	private final InquiryAttachmentService attachmentService = mock(InquiryAttachmentService.class);
	private final InquiryServiceImpl service = new InquiryServiceImpl(mapper, attachmentService,
			mock(egovframework.eeum.notification.service.notification.NotificationEmitter.class));

	private static AuthUser member(long sn) {
		return new AuthUser(sn, "u@eeum.kr", "홍길동", "MEMBER", "ACTIVE", List.of("CHNL_LOGIN_USER"));
	}

	private static AuthUser operator(long sn) {
		return new AuthUser(sn, "op@eeum.kr", "운영자", "OPERATOR", "ACTIVE", List.of("INQR_ANS"));
	}

	private static InqrInfoVO vo(long inqrSn, long userSn, String prvtYn, String pstgStts, String ansStts) {
		return InqrInfoVO.builder().inqrSn(inqrSn).userSn(userSn).inqrTypeCd("ETC")
				.inqrTtl("t").inqrCn("c").prvtPstYn(prvtYn).pstgSttsCd(pstgStts).ansSttsCd(ansStts).delYn("N").build();
	}

	private void stubInternalDetail(long ownerSn) {
		when(mapper.selectInquiryDetail(anyLong())).thenReturn(vo(1L, ownerSn, "N", "NORMAL", "PENDING"));
		when(mapper.selectAnswersByInquiry(anyLong())).thenReturn(List.of());
	}

	@Test
	@DisplayName("비밀글 미지정 시 유형별 기본값 — 예약=비밀, 기타=공개, 명시 false 우선")
	void secretDefaultByType() {
		stubInternalDetail(7L);
		when(mapper.countActiveInqrType(anyString())).thenReturn(1);
		doAnswer(inv -> {
			inv.getArgument(0, InqrInfoVO.class).setInqrSn(1L);
			return null;
		}).when(mapper).insertInquiry(any(InqrInfoVO.class));
		ArgumentCaptor<InqrInfoVO> cap = ArgumentCaptor.forClass(InqrInfoVO.class);

		service.insert(new InqrInsertRequest("RESERVATION", "제목", "내용", null, null), member(7));
		service.insert(new InqrInsertRequest("ETC", "제목", "내용", null, null), member(7));
		service.insert(new InqrInsertRequest("RESERVATION", "제목", "내용", false, null), member(7));

		verify(mapper, times(3)).insertInquiry(cap.capture());
		assertEquals("Y", cap.getAllValues().get(0).getPrvtPstYn(), "예약=기본 비밀");
		assertEquals("N", cap.getAllValues().get(1).getPrvtPstYn(), "기타=기본 공개");
		assertEquals("N", cap.getAllValues().get(2).getPrvtPstYn(), "명시 false 우선");
	}

	@Test
	@DisplayName("타인 글 수정은 FORBIDDEN")
	void updateForbiddenForNonOwner() {
		when(mapper.selectInquiryForUpdate(5L)).thenReturn(vo(5L, 999L, "N", "NORMAL", "PENDING"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.update(new InqrUpdateRequest(5L, "ETC", "t", "c", null, null), member(7)));
		assertEquals(InquiryErrorCode.INQUIRY_FORBIDDEN, e.getErrorCode());
		verify(mapper, never()).updateInquiry(anyLong(), anyLong(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("답변완료 글은 수정 잠금(LOCKED)")
	void updateLockedWhenAnswered() {
		when(mapper.selectInquiryForUpdate(5L)).thenReturn(vo(5L, 7L, "N", "NORMAL", "ANSWERED"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.update(new InqrUpdateRequest(5L, "ETC", "t", "c", null, null), member(7)));
		assertEquals(InquiryErrorCode.INQUIRY_LOCKED, e.getErrorCode());
	}

	@Test
	@DisplayName("PENDING이라도 활성 답변 있으면 수정 잠금")
	void updateLockedWhenActiveAnswerExists() {
		when(mapper.selectInquiryForUpdate(5L)).thenReturn(vo(5L, 7L, "N", "NORMAL", "PENDING"));
		when(mapper.countActiveAnswers(5L)).thenReturn(1);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.update(new InqrUpdateRequest(5L, "ETC", "t", "c", null, null), member(7)));
		assertEquals(InquiryErrorCode.INQUIRY_LOCKED, e.getErrorCode());
	}

	@Test
	@DisplayName("본인 + PENDING + 답변없음이면 수정 성공")
	void updateSuccess() {
		when(mapper.selectInquiryForUpdate(5L)).thenReturn(vo(5L, 7L, "N", "NORMAL", "PENDING"));
		when(mapper.countActiveAnswers(5L)).thenReturn(0);
		when(mapper.countActiveInqrType(anyString())).thenReturn(1);
		service.update(new InqrUpdateRequest(5L, "ETC", "수정제목", "수정내용", true, null), member(7));
		verify(mapper).updateInquiry(5L, 7L, "ETC", "수정제목", "수정내용", "Y");
	}

	@Test
	@DisplayName("비밀글 상세 — 비로그인은 404")
	void secretDetailHiddenFromAnonymous() {
		when(mapper.selectInquiryDetail(5L)).thenReturn(vo(5L, 7L, "Y", "NORMAL", "PENDING"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.detail(5L, null));
		assertEquals(InquiryErrorCode.INQUIRY_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("비밀글 상세 — 작성자/운영자는 열람")
	void secretDetailVisibleToOwnerAndOperator() {
		when(mapper.selectInquiryDetail(5L)).thenReturn(vo(5L, 7L, "Y", "NORMAL", "PENDING"));
		when(mapper.selectAnswersByInquiry(5L)).thenReturn(List.of());
		service.detail(5L, member(7));
		service.detail(5L, operator(2));
	}

	@Test
	@DisplayName("블라인드 글 상세 — 일반 회원은 404")
	void blindedDetailHiddenFromOthers() {
		when(mapper.selectInquiryDetail(5L)).thenReturn(vo(5L, 7L, "N", "BLINDED", "PENDING"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.detail(5L, member(999)));
		assertEquals(InquiryErrorCode.INQUIRY_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("공개글 상세 — 비로그인도 열람")
	void publicDetailVisibleToAnonymous() {
		when(mapper.selectInquiryDetail(5L)).thenReturn(vo(5L, 7L, "N", "NORMAL", "ANSWERED"));
		when(mapper.selectAnswersByInquiry(5L)).thenReturn(List.of());
		service.detail(5L, null);
	}

	@Test
	@DisplayName("문의 삭제 — 본인 활성글이면 soft delete 후 연결 첨부 정리(codex Med 2)")
	void deleteAlsoDetachesAttachments() {
		when(mapper.selectInquiryForUpdate(5L)).thenReturn(vo(5L, 7L, "N", "NORMAL", "PENDING"));
		AuthUser user = member(7);
		service.delete(5L, user);
		verify(mapper).softDeleteInquiry(5L, 7L);
		verify(attachmentService).detachAllByInquiry(5L, user);
	}

	@Test
	@DisplayName("문의 삭제 — 타인 글이면 FORBIDDEN(첨부 정리 진입 안 함)")
	void deleteForbiddenForNonOwner() {
		when(mapper.selectInquiryForUpdate(5L)).thenReturn(vo(5L, 999L, "N", "NORMAL", "PENDING"));
		BusinessException e = assertThrows(BusinessException.class, () -> service.delete(5L, member(7)));
		assertEquals(InquiryErrorCode.INQUIRY_FORBIDDEN, e.getErrorCode());
		verify(mapper, never()).softDeleteInquiry(anyLong(), anyLong());
		verify(attachmentService, never()).detachAllByInquiry(anyLong(), any());
	}

	@Test
	@DisplayName("블라인드 대상 없으면 404")
	void blindNotFound() {
		when(mapper.blindInquiry(5L, "사유", 2L)).thenReturn(0);
		BusinessException e = assertThrows(BusinessException.class,
				() -> service.blind(new InqrBlindRequest(5L, "사유"), operator(2)));
		assertEquals(InquiryErrorCode.INQUIRY_NOT_FOUND, e.getErrorCode());
	}
}
