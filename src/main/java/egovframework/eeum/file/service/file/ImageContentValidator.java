package egovframework.eeum.file.service.file;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 이미지 매직바이트 검증 — 확장자/선언 MIME 만 믿지 않고 실제 시그니처를 확인한다(확장자 위조 차단, codex 권고).
 * 허용: PNG / JPEG / GIF / WEBP. 추가로 디코딩 없이 해상도(픽셀 수)를 읽어 픽셀폭탄을 방어한다.
 *
 * <p>WEBP(CT-W1.1): JDK ImageIO 가 webp 를 못 읽으므로 외부 라이브러리 없이 RIFF/WEBP 컨테이너 헤더를
 * 직접 파싱해 치수를 얻는다(VP8 lossy / VP8L lossless / VP8X extended). 판독 불가 webp 는 -1(거부).
 */
@Slf4j
@Component
public class ImageContentValidator {

	/** 시그니처 판별에 필요한 최소 선행 바이트 수(매직바이트 매칭용 — matches). */
	public static final int HEADER_BYTES = 12;

	/** WEBP 치수 파싱에 필요한 선행 바이트(VP8/VP8X 는 30바이트까지 필요). */
	private static final int WEBP_PROBE_BYTES = 30;

	private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
	private static final byte[] GIF87 = {'G', 'I', 'F', '8', '7', 'a'};
	private static final byte[] GIF89 = {'G', 'I', 'F', '8', '9', 'a'};
	private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
	private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

	/** 확장자(소문자)와 헤더 시그니처가 일치하는지. */
	public boolean matches(String extLower, byte[] header) {
		if (extLower == null || header == null) {
			return false;
		}
		return switch (extLower.toLowerCase(Locale.ROOT)) {
			case "png" -> startsWith(header, PNG);
			case "jpg", "jpeg" -> startsWith(header, JPEG);
			case "gif" -> startsWith(header, GIF87) || startsWith(header, GIF89);
			case "webp" -> isWebpContainer(header);
			default -> false;
		};
	}

	/**
	 * 이미지 해상도(픽셀 수 = width×height)를 <b>디코딩 없이</b> 헤더에서 읽는다(픽셀폭탄 → OOM 방어).
	 * WEBP 는 RIFF 헤더 직접 파싱, 그 외(PNG/JPEG/GIF)는 ImageIO 헤더 판독.
	 * 표준 포맷이 아니거나 손상돼 판독 불가하면 -1 을 반환한다(호출 측이 거부 처리).
	 */
	public long readPixelCount(InputStream in) {
		BufferedInputStream buffered = (in instanceof BufferedInputStream b) ? b : new BufferedInputStream(in);

		// WEBP 선판별 — 선행 바이트만 엿보고(mark/reset) RIFF/WEBP 면 직접 파싱, 아니면 스트림을 되돌려 ImageIO 로.
		try {
			buffered.mark(WEBP_PROBE_BYTES + 1);
			byte[] head = buffered.readNBytes(WEBP_PROBE_BYTES);
			buffered.reset();
			if (isWebpContainer(head)) {
				return readWebpPixelCount(head);
			}
		} catch (IOException | RuntimeException e) {
			log.debug("[FILE] webp 헤더 프로브 실패 — 형식 오류로 처리", e);
			return -1L;
		}

		try (ImageInputStream iis = ImageIO.createImageInputStream(buffered)) {
			if (iis == null) {
				return -1L;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (!readers.hasNext()) {
				return -1L;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(iis, true, true);
				long width = reader.getWidth(0);
				long height = reader.getHeight(0);
				return width * height;
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			// 형식 오류/판독 불가는 호출 측이 FILE_CONTENT_INVALID 로 거부(fail-closed). 진단을 위해 원인은 debug 로 남긴다.
			log.debug("[FILE] 이미지 해상도 판독 실패 — 형식 오류로 처리", e);
			return -1L;
		}
	}

	/** 확장자(소문자) → 표준 MIME. 미지원이면 null. */
	public String mimeOf(String extLower) {
		if (extLower == null) {
			return null;
		}
		return switch (extLower.toLowerCase(Locale.ROOT)) {
			case "png" -> "image/png";
			case "jpg", "jpeg" -> "image/jpeg";
			case "gif" -> "image/gif";
			case "webp" -> "image/webp";
			default -> null;
		};
	}

	// ------------------------------------------------------------------
	// WEBP (RIFF 컨테이너) — 외부 라이브러리 없이 헤더 파싱
	// ------------------------------------------------------------------

	/** RIFF....WEBP 컨테이너인지(바이트 0-3='RIFF', 8-11='WEBP'). */
	private static boolean isWebpContainer(byte[] header) {
		return header != null && header.length >= 12
				&& startsWith(header, RIFF) && regionEquals(header, 8, WEBP);
	}

	/**
	 * WEBP 치수 → 픽셀 수. 서브포맷(VP8 lossy / VP8L lossless / VP8X extended)별로 치수 위치가 다르다.
	 * 헤더 부족·미지 포맷·시그니처 불일치·치수 0 이하는 -1(판독 불가 → 거부).
	 */
	private static long readWebpPixelCount(byte[] h) {
		if (h == null || h.length < 16) {
			return -1L; // fourCC(12-15) 까지는 있어야 함
		}
		String fourCc = new String(h, 12, 4, StandardCharsets.US_ASCII);
		switch (fourCc) {
			case "VP8 ": { // lossy — 시작코드 0x9D 0x01 0x2A 후 14-bit width/height(LE)
				if (h.length < 30
						|| (h[23] & 0xFF) != 0x9D || (h[24] & 0xFF) != 0x01 || (h[25] & 0xFF) != 0x2A) {
					return -1L;
				}
				int w = ((h[26] & 0xFF) | ((h[27] & 0xFF) << 8)) & 0x3FFF;
				int hh = ((h[28] & 0xFF) | ((h[29] & 0xFF) << 8)) & 0x3FFF;
				return dimensions(w, hh);
			}
			case "VP8L": { // lossless — 시그니처 0x2F, 이후 14-bit(width-1)/14-bit(height-1)
				if (h.length < 25 || (h[20] & 0xFF) != 0x2F) {
					return -1L;
				}
				int bits = (h[21] & 0xFF) | ((h[22] & 0xFF) << 8) | ((h[23] & 0xFF) << 16) | ((h[24] & 0xFF) << 24);
				int w = (bits & 0x3FFF) + 1;
				int hh = ((bits >> 14) & 0x3FFF) + 1;
				return dimensions(w, hh);
			}
			case "VP8X": { // extended — 24-bit(canvas width-1)/24-bit(canvas height-1)
				if (h.length < 30) {
					return -1L;
				}
				int w = (((h[24] & 0xFF) | ((h[25] & 0xFF) << 8) | ((h[26] & 0xFF) << 16)) & 0xFFFFFF) + 1;
				int hh = (((h[27] & 0xFF) | ((h[28] & 0xFF) << 8) | ((h[29] & 0xFF) << 16)) & 0xFFFFFF) + 1;
				return dimensions(w, hh);
			}
			default:
				return -1L;
		}
	}

	private static long dimensions(int width, int height) {
		if (width <= 0 || height <= 0) {
			return -1L;
		}
		return (long) width * (long) height;
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		if (data.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (data[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean regionEquals(byte[] data, int offset, byte[] expected) {
		if (data.length < offset + expected.length) {
			return false;
		}
		for (int i = 0; i < expected.length; i++) {
			if (data[offset + i] != expected[i]) {
				return false;
			}
		}
		return true;
	}
}
