package egovframework.eeum.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import egovframework.eeum.file.service.file.ImageReencoder;

/**
 * 이미지 재인코딩 검증 — 디코드→재인코딩으로 메타데이터 제거(유효 이미지 출력) + 픽셀 상한 방어 + 손상 입력 거부.
 */
class ImageReencoderVerification {

	private final ImageReencoder reencoder = new ImageReencoder();

	private static byte[] image(int w, int h, String format) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int type = "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
			ImageIO.write(new BufferedImage(w, h, type), format, out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("테스트 이미지 생성 실패", e);
		}
	}

	@Test
	@DisplayName("PNG 재인코딩 — PNG 포맷 유지, 디코딩 가능한 유효 이미지 반환")
	void reencodePngKeepsPng() throws IOException {
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", image(4, 4, "png"));
		ImageReencoder.Result r = reencoder.reencode(f, 25_000_000L);
		assertEquals("png", r.formatName());
		assertEquals("image/png", r.mimeType());
		assertNotNull(ImageIO.read(new ByteArrayInputStream(r.bytes()))); // 재인코딩 결과가 유효 이미지
	}

	@Test
	@DisplayName("JPEG 재인코딩 — JPEG 로 출력(메타데이터 없는 재인코딩)")
	void reencodeJpegOutputsJpeg() throws IOException {
		MockMultipartFile f = new MockMultipartFile("file", "a.jpg", "image/jpeg", image(4, 4, "jpg"));
		ImageReencoder.Result r = reencoder.reencode(f, 25_000_000L);
		assertEquals("jpg", r.formatName());
		assertEquals("image/jpeg", r.mimeType());
		assertNotNull(ImageIO.read(new ByteArrayInputStream(r.bytes())));
	}

	@Test
	@DisplayName("해상도 상한 초과는 FILE_DIMENSION_TOO_LARGE (디코딩 전 방어)")
	void pixelLimitRejected() {
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", image(4, 4, "png"));
		BusinessException e = assertThrows(BusinessException.class, () -> reencoder.reencode(f, 1L)); // 16px > 1
		assertEquals(FileErrorCode.FILE_DIMENSION_TOO_LARGE, e.getErrorCode());
	}

	@Test
	@DisplayName("이미지가 아닌 바이트는 FILE_CONTENT_INVALID")
	void corruptInputRejected() {
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png",
				new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
		BusinessException e = assertThrows(BusinessException.class, () -> reencoder.reencode(f, 25_000_000L));
		assertEquals(FileErrorCode.FILE_CONTENT_INVALID, e.getErrorCode());
	}

	@Test
	@DisplayName("maxPixels 0 이하면 픽셀 검사 생략하고 정상 재인코딩")
	void pixelCheckSkippedWhenZero() {
		MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", image(4, 4, "png"));
		ImageReencoder.Result r = reencoder.reencode(f, 0L);
		assertTrue(r.bytes().length > 0);
	}
}
