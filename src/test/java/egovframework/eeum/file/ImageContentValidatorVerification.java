package egovframework.eeum.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import egovframework.eeum.file.service.file.ImageContentValidator;

/**
 * 이미지 매직바이트 검증 — 확장자 위조(확장자만 png, 내용은 다른 것) 차단.
 */
class ImageContentValidatorVerification {

	private final ImageContentValidator validator = new ImageContentValidator();

	private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
	private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
	private static final byte[] GIF_HEADER = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0};

	@Test
	@DisplayName("올바른 시그니처는 확장자와 일치하면 통과")
	void validSignatures() {
		assertTrue(validator.matches("png", PNG_HEADER));
		assertTrue(validator.matches("jpg", JPEG_HEADER));
		assertTrue(validator.matches("jpeg", JPEG_HEADER));
		assertTrue(validator.matches("gif", GIF_HEADER));
		assertTrue(validator.matches("PNG", PNG_HEADER), "확장자 대소문자 무관");
	}

	@Test
	@DisplayName("확장자 위조(png 확장자 + 비-png 내용)는 거부")
	void rejectSpoofedExtension() {
		assertFalse(validator.matches("png", JPEG_HEADER));
		assertFalse(validator.matches("gif", PNG_HEADER));
		assertFalse(validator.matches("png", new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));
	}

	@Test
	@DisplayName("허용되지 않은 확장자/짧은 헤더/null 은 거부")
	void rejectInvalid() {
		assertFalse(validator.matches("txt", PNG_HEADER));
		assertFalse(validator.matches("png", new byte[] {(byte) 0x89, 0x50}), "헤더가 짧으면 거부");
		assertFalse(validator.matches(null, PNG_HEADER));
		assertFalse(validator.matches("png", null));
	}

	@Test
	@DisplayName("확장자 → 표준 MIME 매핑")
	void mimeMapping() {
		assertEquals("image/png", validator.mimeOf("png"));
		assertEquals("image/jpeg", validator.mimeOf("jpg"));
		assertEquals("image/jpeg", validator.mimeOf("jpeg"));
		assertEquals("image/gif", validator.mimeOf("gif"));
		assertEquals("image/webp", validator.mimeOf("webp"));
	}

	// ------------------------------------------------------------------
	// WEBP (CT-W1.1)
	// ------------------------------------------------------------------

	@Test
	@DisplayName("webp: 올바른 RIFF/WEBP 컨테이너는 matches 통과, 위조는 거부")
	void webpMagicBytes() {
		assertTrue(validator.matches("webp", webp("VP8X", vp8xDims(2, 3))), "정상 RIFF/WEBP");
		assertTrue(validator.matches("WEBP", webp("VP8L", vp8lDims(1, 1))), "확장자 대소문자 무관");
		assertFalse(validator.matches("webp", JPEG_HEADER), "확장자 webp + 비-webp 내용 거부");
		assertFalse(validator.matches("webp", new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'X', 'X', 'X', 'X'}),
				"RIFF 이지만 WEBP 아님 거부");
		assertFalse(validator.matches("webp", new byte[] {'R', 'I', 'F', 'F'}), "짧은 헤더 거부");
	}

	@Test
	@DisplayName("webp 치수 판독 — VP8/VP8L/VP8X 서브포맷별 픽셀 수")
	void webpPixelCount() {
		assertEquals(6L, pixels(webp("VP8X", vp8xDims(2, 3))), "VP8X 2x3");
		assertEquals(64_000_000L, pixels(webp("VP8X", vp8xDims(8000, 8000))), "VP8X 8000x8000(픽셀상한 초과 소스)");
		assertEquals(1L, pixels(webp("VP8L", vp8lDims(1, 1))), "VP8L 1x1");
		assertEquals(20L, pixels(webp("VP8 ", vp8Dims(4, 5))), "VP8 lossy 4x5");
	}

	@Test
	@DisplayName("webp 판독 불가(미지 청크/시그니처 위조/치수0) → -1 (거부)")
	void webpUnreadable() {
		assertEquals(-1L, pixels(webp("ZZZZ", new byte[10])), "미지 서브포맷");
		// VP8 시작코드(0x9D 0x01 0x2A) 불일치
		byte[] badVp8 = webp("VP8 ", new byte[] {0, 0, 0, 0x11, 0x22, 0x33, 4, 0, 5, 0});
		assertEquals(-1L, pixels(badVp8), "VP8 시작코드 불일치");
		// VP8L 시그니처(0x2F) 불일치
		byte[] badVp8l = webp("VP8L", new byte[] {0x00, 0, 0, 0, 0});
		assertEquals(-1L, pixels(badVp8l), "VP8L 시그니처 불일치");
		// 치수 0(VP8X width-1/height-1 최대? → 여기선 VP8 0x0 → -1)
		assertEquals(-1L, pixels(webp("VP8 ", new byte[] {0, 0, 0, (byte) 0x9D, 0x01, 0x2A, 0, 0, 0, 0})), "0x0 거부");
	}

	@Test
	@DisplayName("회귀: jpg/jpeg/png 는 기존과 동일하게 통과, png 치수 판독 유지")
	void nonWebpRegression() {
		assertTrue(validator.matches("png", PNG_HEADER));
		assertTrue(validator.matches("jpg", JPEG_HEADER));
		assertFalse(validator.matches("png", JPEG_HEADER), "위조 거부 유지");
		// 1x1 PNG 실제 바이트 → ImageIO 경로가 그대로 동작(픽셀=1)
		byte[] png1x1 = Base64.getDecoder().decode(
				"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQAY3Y2wAAAAAElFTkSuQmCC");
		assertEquals(1L, pixels(png1x1), "png 1x1 치수 판독(비-webp 경로 회귀 없음)");
	}

	// --- helpers ---

	private long pixels(byte[] bytes) {
		return validator.readPixelCount(new ByteArrayInputStream(bytes));
	}

	/** RIFF[0-3] + size[4-7] + WEBP[8-11] + fourCc[12-15] + chunkSize[16-19] + afterChunkSize@20.. (총 30바이트). */
	private static byte[] webp(String fourCc, byte[] afterChunkSize) {
		byte[] h = new byte[30];
		putAscii(h, 0, "RIFF");
		putAscii(h, 8, "WEBP");
		putAscii(h, 12, fourCc);
		if (afterChunkSize != null) {
			System.arraycopy(afterChunkSize, 0, h, 20, Math.min(afterChunkSize.length, 10));
		}
		return h;
	}

	/** VP8X: flags[20] + reserved[21-23] + (width-1)@24-26(24bit LE) + (height-1)@27-29. offset 20 기준 10바이트. */
	private static byte[] vp8xDims(int w, int h) {
		int w1 = w - 1;
		int h1 = h - 1;
		return new byte[] {0,
				0, 0, 0,
				(byte) (w1 & 0xFF), (byte) ((w1 >> 8) & 0xFF), (byte) ((w1 >> 16) & 0xFF),
				(byte) (h1 & 0xFF), (byte) ((h1 >> 8) & 0xFF), (byte) ((h1 >> 16) & 0xFF)};
	}

	/** VP8L: 시그니처 0x2F[20] + 4바이트(14bit width-1 | 14bit height-1)@21-24. offset 20 기준 5바이트. */
	private static byte[] vp8lDims(int w, int h) {
		int bits = ((w - 1) & 0x3FFF) | (((h - 1) & 0x3FFF) << 14);
		return new byte[] {0x2F,
				(byte) (bits & 0xFF), (byte) ((bits >> 8) & 0xFF), (byte) ((bits >> 16) & 0xFF), (byte) ((bits >> 24) & 0xFF)};
	}

	/** VP8 lossy: frameTag[20-22] + 시작코드 0x9D 0x01 0x2A[23-25] + width@26-27(14bit LE) + height@28-29. */
	private static byte[] vp8Dims(int w, int h) {
		return new byte[] {0, 0, 0,
				(byte) 0x9D, 0x01, 0x2A,
				(byte) (w & 0xFF), (byte) ((w >> 8) & 0x3F),
				(byte) (h & 0xFF), (byte) ((h >> 8) & 0x3F)};
	}

	private static void putAscii(byte[] target, int offset, String s) {
		byte[] b = s.getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(b, 0, target, offset, b.length);
	}
}
