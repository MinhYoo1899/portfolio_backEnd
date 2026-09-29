package egovframework.eeum.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import egovframework.eeum.account.service.account.ProfileImagePolicyProperties;
import egovframework.eeum.account.service.account.impl.ProfileImageServiceImpl;
import egovframework.eeum.account.service.account.impl.ProfileImageSwapper;
import egovframework.eeum.account.web.dto.ProfileImageResponse;
import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.service.file.ImageContentValidator;
import egovframework.eeum.file.service.file.ImageReencoder;
import egovframework.eeum.file.service.file.ImageStorePolicy;
import egovframework.eeum.file.web.dto.FileUploadResponse;

/**
 * 프로필 이미지 서비스 검증 — 즉시 교체(재인코딩→저장→교체→이전 soft delete)와 본인 대상 처리(SFTP/DB 없이 mock).
 * 원본 입력 검증(확장자/매직바이트)은 실제 {@link ImageContentValidator} 로 수행(우회 차단 검증 — codex High 2).
 */
class ProfileImageServiceVerification {

	/** 실제 디코딩 가능한 1×1 이미지 — 매직바이트 검증을 통과하기 위한 진짜 시그니처. */
	private static final byte[] PNG = tiny("png");
	private static final byte[] JPEG = tiny("jpg");
	private static final byte[] GIF = tiny("gif");

	private static byte[] tiny(String fmt) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), fmt, out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("테스트용 이미지 생성 실패: " + fmt, e);
		}
	}

	private final FileService fileService = mock(FileService.class);
	private final ImageReencoder imageReencoder = mock(ImageReencoder.class);
	private final ProfileImageSwapper swapper = mock(ProfileImageSwapper.class);
	private final ProfileImagePolicyProperties policy = newPolicy(DataSize.ofMegabytes(5));

	private final ProfileImageServiceImpl service =
			new ProfileImageServiceImpl(fileService, imageReencoder, new ImageContentValidator(), policy, swapper);

	private static ProfileImagePolicyProperties newPolicy(DataSize maxSize) {
		ProfileImagePolicyProperties p = new ProfileImagePolicyProperties();
		p.setMaxSize(maxSize);
		return p;
	}

	private static FileUploadResponse stored(long atchFileSn) {
		return new FileUploadResponse(atchFileSn, "profile.png",
				"/files/download?atchFileSn=" + atchFileSn, "image/png", "png", 100L);
	}

	@Test
	@DisplayName("정상 교체 — 재인코딩 후 PROFILE_IMAGE 스코프로 저장하고 본인 행을 교체")
	void replaceSuccess() {
		when(imageReencoder.reencode(any(), anyLong()))
				.thenReturn(new ImageReencoder.Result(new byte[] {1, 2, 3}, "png", "image/png"));
		when(fileService.storeImage(any(), eq(7L), eq("USER_PRFL"), eq("7"), any()))
				.thenReturn(stored(42L));

		MockMultipartFile f = new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG);
		ProfileImageResponse result = service.replace(7L, f);

		assertEquals(42L, result.atchFileSn());
		// 저장 스코프가 PROFILE_IMAGE 인지 확인 (공개이미지 업로드 제한 우회 방지 — 본인 인증만)
		ArgumentCaptor<ImageStorePolicy> policyCaptor = ArgumentCaptor.forClass(ImageStorePolicy.class);
		verify(fileService).storeImage(any(), eq(7L), eq("USER_PRFL"), eq("7"), policyCaptor.capture());
		assertEquals("PROFILE_IMAGE", policyCaptor.getValue().fileScopeCd());
		// 행 잠금 교체(이전 파일 soft delete 포함)는 swapper 가 담당
		verify(swapper).swap(7L, 42L);
	}

	@Test
	@DisplayName("빈 파일은 FILE_EMPTY — 재인코딩/저장 호출 안 함")
	void emptyFile() {
		MockMultipartFile f = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]);
		BusinessException e = assertThrows(BusinessException.class, () -> service.replace(7L, f));
		assertEquals(FileErrorCode.FILE_EMPTY, e.getErrorCode());
		verify(imageReencoder, never()).reencode(any(), anyLong());
		verify(fileService, never()).storeImage(any(), anyLong(), any(), any(), any());
	}

	@Test
	@DisplayName("크기 초과는 FILE_TOO_LARGE — 재인코딩 호출 안 함")
	void tooLarge() {
		ProfileImageServiceImpl tiny = new ProfileImageServiceImpl(
				fileService, imageReencoder, new ImageContentValidator(), newPolicy(DataSize.ofBytes(2)), swapper);
		MockMultipartFile f = new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG); // 실제 JPEG > 2바이트
		BusinessException e = assertThrows(BusinessException.class, () -> tiny.replace(7L, f));
		assertEquals(FileErrorCode.FILE_TOO_LARGE, e.getErrorCode());
		verify(imageReencoder, never()).reencode(any(), anyLong());
	}

	@Test
	@DisplayName("GIF 를 프로필로 올리면 거부 — 재인코딩으로 우회 통과 불가 (codex High 2)")
	void gifRejectedBeforeReencode() {
		// GIF 는 ImageIO 가 디코딩 가능해 재인코딩 검증만으로는 JPEG 로 변환·통과될 수 있다.
		// 원본 확장자/매직바이트 선검증으로 재인코딩 이전에 거부해야 한다.
		MockMultipartFile gifByExt = new MockMultipartFile("file", "a.gif", "image/gif", GIF);
		BusinessException e1 = assertThrows(BusinessException.class, () -> service.replace(7L, gifByExt));
		assertEquals(FileErrorCode.FILE_EXTENSION_NOT_ALLOWED, e1.getErrorCode());

		// 확장자를 png 로 위장해도 매직바이트(GIF 시그니처)가 png 와 불일치 → FILE_CONTENT_INVALID
		MockMultipartFile gifSpoofed = new MockMultipartFile("file", "a.png", "image/png", GIF);
		BusinessException e2 = assertThrows(BusinessException.class, () -> service.replace(7L, gifSpoofed));
		assertEquals(FileErrorCode.FILE_CONTENT_INVALID, e2.getErrorCode());

		// 어느 경우도 재인코딩/저장으로 진행되지 않는다
		verify(imageReencoder, never()).reencode(any(), anyLong());
		verify(fileService, never()).storeImage(any(), anyLong(), any(), any(), any());
	}

	@Test
	@DisplayName("교체(swap) 실패 시 방금 저장한 새 파일을 보상 soft delete")
	void compensateOnSwapFailure() {
		when(imageReencoder.reencode(any(), anyLong()))
				.thenReturn(new ImageReencoder.Result(new byte[] {1, 2, 3}, "png", "image/png"));
		when(fileService.storeImage(any(), eq(7L), any(), any(), any())).thenReturn(stored(99L));
		doThrow(new BusinessException(FileErrorCode.FILE_NOT_FOUND)).when(swapper).swap(7L, 99L);

		MockMultipartFile f = new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG);
		assertThrows(BusinessException.class, () -> service.replace(7L, f));
		verify(fileService).softDeleteByAtchFileSn(99L, 7L); // 보상 정리(고아 방지)
	}
}
