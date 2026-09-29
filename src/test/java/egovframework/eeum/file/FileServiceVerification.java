package egovframework.eeum.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import egovframework.eeum.auth.vo.AuthUser;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.FilePolicyProperties;
import egovframework.eeum.file.service.file.ImageContentValidator;
import egovframework.eeum.file.service.file.dao.FileMapper;
import egovframework.eeum.file.service.file.impl.FileServiceImpl;
import egovframework.eeum.file.service.file.storage.FileStorage;
import egovframework.eeum.file.vo.AtchFileVO;
import egovframework.eeum.file.vo.UploadUsageVO;

/**
 * 파일 서비스 검증 — 업로드 검증 분기 + 삭제 소유권(SFTP/DB 없이 mock).
 */
class FileServiceVerification {

	/** 실제 디코딩 가능한 1×1 PNG — 매직바이트 + 픽셀 판독을 모두 통과(픽셀폭탄 방어 도입 이후). */
	private static final byte[] PNG = tinyPng();

	private static byte[] tinyPng() {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("테스트용 PNG 생성 실패", e);
		}
	}

	private final FileMapper fileMapper = mock(FileMapper.class);
	private final FileStorage fileStorage = mock(FileStorage.class);
	private final FilePolicyProperties policy = newPolicy(DataSize.ofMegabytes(50));
	private final FileServiceImpl service =
			new FileServiceImpl(fileMapper, fileStorage, policy, new ImageContentValidator());

	private static FilePolicyProperties newPolicy(DataSize maxSize) {
		FilePolicyProperties p = new FilePolicyProperties();
		p.setMaxSize(maxSize);
		p.setAllowedExtensions(List.of("gif", "jpg", "jpeg", "png"));
		return p;
	}

	private static AuthUser user(long sn, String type) {
		return new AuthUser(sn, "u@eeum.kr", "홍길동", type, "ACTIVE", List.of());
	}

	@Test
	@DisplayName("저장소 미설정이면 503")
	void storageUnavailable() {
		when(fileStorage.isAvailable()).thenReturn(false);
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", PNG);
		BusinessException e = assertThrows(BusinessException.class, () -> service.upload(f, 1L, null, null));
		assertEquals(FileErrorCode.FILE_STORAGE_UNAVAILABLE, e.getErrorCode());
	}

	@Test
	@DisplayName("빈 파일은 FILE_EMPTY")
	void emptyFile() {
		when(fileStorage.isAvailable()).thenReturn(true);
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
		BusinessException e = assertThrows(BusinessException.class, () -> service.upload(f, 1L, null, null));
		assertEquals(FileErrorCode.FILE_EMPTY, e.getErrorCode());
	}

	@Test
	@DisplayName("허용되지 않은 확장자는 FILE_EXTENSION_NOT_ALLOWED")
	void badExtension() {
		when(fileStorage.isAvailable()).thenReturn(true);
		MockMultipartFile f = new MockMultipartFile("file", "a.txt", "text/plain", PNG);
		BusinessException e = assertThrows(BusinessException.class, () -> service.upload(f, 1L, null, null));
		assertEquals(FileErrorCode.FILE_EXTENSION_NOT_ALLOWED, e.getErrorCode());
	}

	@Test
	@DisplayName("크기 초과는 FILE_TOO_LARGE (413)")
	void tooLarge() {
		FileServiceImpl tiny = new FileServiceImpl(fileMapper, fileStorage, newPolicy(DataSize.ofBytes(4)),
				new ImageContentValidator());
		when(fileStorage.isAvailable()).thenReturn(true);
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", PNG); // 12 bytes > 4
		BusinessException e = assertThrows(BusinessException.class, () -> tiny.upload(f, 1L, null, null));
		assertEquals(FileErrorCode.FILE_TOO_LARGE, e.getErrorCode());
	}

	@Test
	@DisplayName("확장자 위조(png 확장자 + 비-png 내용)는 FILE_CONTENT_INVALID — 저장 호출 안 함")
	void spoofedContent() {
		when(fileStorage.isAvailable()).thenReturn(true);
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png",
				new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
		BusinessException e = assertThrows(BusinessException.class, () -> service.upload(f, 1L, null, null));
		assertEquals(FileErrorCode.FILE_CONTENT_INVALID, e.getErrorCode());
		verify(fileStorage, never()).store(any(), any());
	}

	@Test
	@DisplayName("해상도(픽셀 수) 초과는 FILE_DIMENSION_TOO_LARGE — 저장 호출 안 함")
	void dimensionTooLarge() {
		FilePolicyProperties p = newPolicy(DataSize.ofMegabytes(50));
		p.setMaxPixels(0L); // 1픽셀(1×1)도 초과로 판정
		FileServiceImpl svc = new FileServiceImpl(fileMapper, fileStorage, p, new ImageContentValidator());
		when(fileStorage.isAvailable()).thenReturn(true);
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", PNG);
		BusinessException e = assertThrows(BusinessException.class, () -> svc.upload(f, 1L, null, null));
		assertEquals(FileErrorCode.FILE_DIMENSION_TOO_LARGE, e.getErrorCode());
		verify(fileStorage, never()).store(any(), any());
	}

	@Test
	@DisplayName("업로드 쿼터(건수) 초과는 FILE_QUOTA_EXCEEDED — 저장 호출 안 함")
	void quotaExceeded() {
		when(fileStorage.isAvailable()).thenReturn(true);
		when(fileMapper.selectRecentUploadUsage(anyLong(), any()))
				.thenReturn(new UploadUsageVO(policy.getDailyMaxCount(), 0L)); // 이미 상한 도달 → +1 초과
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", PNG);
		BusinessException e = assertThrows(BusinessException.class, () -> service.upload(f, 1L, null, null));
		assertEquals(FileErrorCode.FILE_QUOTA_EXCEEDED, e.getErrorCode());
		verify(fileStorage, never()).store(any(), any());
	}

	@Test
	@DisplayName("정상 업로드 — 저장 후 메타 등록, 식별자 반환")
	void uploadSuccess() {
		when(fileStorage.isAvailable()).thenReturn(true);
		when(fileStorage.storageTypeCode()).thenReturn("SFTP");
		doAnswer(inv -> {
			inv.getArgument(0, AtchFileVO.class).setAtchFileSn(10L);
			return null;
		}).when(fileMapper).insertAtchFile(any(AtchFileVO.class));

		MockMultipartFile f = new MockMultipartFile("file", "프로필.png", "image/png", PNG);
		var result = service.upload(f, 7L, "USER_PRFL", "7");

		assertEquals(10L, result.atchFileSn());
		assertEquals("image/png", result.mimeTypeNm());
		verify(fileStorage).store(any(), any());
	}

	@Test
	@DisplayName("메타 등록 실패 시 저장파일 보상 삭제")
	void compensateOnDbFailure() {
		when(fileStorage.isAvailable()).thenReturn(true);
		when(fileStorage.storageTypeCode()).thenReturn("SFTP");
		doThrow(new RuntimeException("db")).when(fileMapper).insertAtchFile(any(AtchFileVO.class));

		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", PNG);
		assertThrows(RuntimeException.class, () -> service.upload(f, 1L, null, null));
		verify(fileStorage).delete(any()); // 보상 삭제
	}

	@Test
	@DisplayName("타인이 삭제하면 FILE_DELETE_FORBIDDEN")
	void deleteForbiddenForNonOwner() {
		AtchFileVO vo = AtchFileVO.builder().atchFileSn(5L).strgKey("public/x.png").rgtrSn(999L).build();
		when(fileMapper.selectDeletablePublicImageBySn(5L)).thenReturn(vo);
		BusinessException e = assertThrows(BusinessException.class, () -> service.delete(5L, user(1L, "MEMBER")));
		assertEquals(FileErrorCode.FILE_DELETE_FORBIDDEN, e.getErrorCode());
		verify(fileMapper, never()).softDeleteAtchFile(anyLong(), anyLong());
	}

	@Test
	@DisplayName("등록자 본인은 삭제 가능")
	void ownerCanDelete() {
		AtchFileVO vo = AtchFileVO.builder().atchFileSn(5L).strgKey("public/x.png").rgtrSn(1L).build();
		when(fileMapper.selectDeletablePublicImageBySn(5L)).thenReturn(vo);
		when(fileMapper.softDeleteAtchFile(5L, 1L)).thenReturn(1);
		service.delete(5L, user(1L, "MEMBER"));
		verify(fileMapper).softDeleteAtchFile(5L, 1L);
		// 물리 삭제는 활성 트랜잭션이 없는 단위테스트에서 즉시 실행됨(afterCommit 분기 — 트랜잭션 없으면 즉시)
		verify(fileStorage).delete("public/x.png");
	}

	@Test
	@DisplayName("운영자는 타인 파일도 삭제 가능")
	void operatorCanDelete() {
		AtchFileVO vo = AtchFileVO.builder().atchFileSn(5L).strgKey("public/x.png").rgtrSn(999L).build();
		when(fileMapper.selectDeletablePublicImageBySn(5L)).thenReturn(vo);
		when(fileMapper.softDeleteAtchFile(5L, 2L)).thenReturn(1);
		service.delete(5L, user(2L, "OPERATOR"));
		verify(fileMapper).softDeleteAtchFile(5L, 2L);
	}

	@Test
	@DisplayName("없는 파일 삭제는 FILE_NOT_FOUND")
	void deleteNotFound() {
		when(fileMapper.selectDeletablePublicImageBySn(404L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.delete(404L, user(1L, "MEMBER")));
		assertEquals(FileErrorCode.FILE_NOT_FOUND, e.getErrorCode());
	}

	@Test
	@DisplayName("프로필 이미지(PROFILE_IMAGE)는 범용 /files/delete 로 삭제 불가 — FILE_NOT_FOUND, soft delete 호출 안 함 (codex High 1)")
	void profileImageNotDeletableViaGenericDelete() {
		// selectDeletablePublicImageBySn 는 PUBLIC_IMAGE 만 매칭 → PROFILE_IMAGE 행은 조회되지 않아 null.
		// (등록자 본인이더라도 이 경로로는 프로필을 못 지운다 — USER_DTL_INFO.PRFL_IMG_FILE_SN 참조 무결성 보호)
		when(fileMapper.selectDeletablePublicImageBySn(77L)).thenReturn(null);
		BusinessException e = assertThrows(BusinessException.class, () -> service.delete(77L, user(1L, "MEMBER")));
		assertEquals(FileErrorCode.FILE_NOT_FOUND, e.getErrorCode());
		verify(fileMapper, never()).softDeleteAtchFile(anyLong(), anyLong());
	}
}
