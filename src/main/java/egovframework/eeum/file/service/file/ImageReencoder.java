package egovframework.eeum.file.service.file;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import egovframework.eeum.base.exception.BusinessException;
import egovframework.eeum.file.exception.FileErrorCode;
import lombok.extern.slf4j.Slf4j;

/**
 * 이미지 재인코딩 — 디코드 후 동일 계열 포맷(jpg/png)으로 재인코딩하여 EXIF/GPS 등 메타데이터를 제거한다.
 *
 * <p>프로필처럼 공개 노출되는 이미지의 위치정보 유출을 막는 용도. 재인코딩 전 해상도(픽셀 수) 상한을 검사해
 * 픽셀폭탄(디코딩 시 OOM)을 방어한다. 공개 이미지(숙소 등)는 재인코딩하지 않으므로 호출하지 않는다.
 */
@Slf4j
@Component
public class ImageReencoder {

	/** 재인코딩 결과 — 메타데이터가 제거된 바이트 + 출력 포맷(확장자/MIME 동기화용). */
	public record Result(byte[] bytes, String formatName, String mimeType) {
	}

	/**
	 * 이미지를 디코드 후 재인코딩하여 메타데이터를 제거한다.
	 *
	 * <p>입력 PNG → PNG, 그 외(JPEG 등)는 JPEG 로 출력(투명도 없는 사진 계열은 JPEG 가 적합).
	 * 디코딩 불가/판독 실패는 {@code FILE_CONTENT_INVALID}, 픽셀 상한 초과는 {@code FILE_DIMENSION_TOO_LARGE}.
	 *
	 * @param file      원본 멀티파트(이미지)
	 * @param maxPixels 디코딩 허용 최대 픽셀 수(width×height) — 0 이하면 검사 생략
	 */
	public Result reencode(MultipartFile file, long maxPixels) {
		byte[] source = readAll(file);
		// 1) 디코딩 전 해상도 상한(픽셀폭탄 방어) — 헤더만 읽어 픽셀 수 확인
		if (maxPixels > 0) {
			long pixels = readPixelCount(source);
			if (pixels < 0) {
				throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
			}
			if (pixels > maxPixels) {
				throw new BusinessException(FileErrorCode.FILE_DIMENSION_TOO_LARGE);
			}
		}
		// 2) 디코딩
		BufferedImage image = decode(source);
		// 3) 출력 포맷 결정 — PNG 만 PNG 유지, 나머지는 JPEG
		boolean png = isPng(source);
		String formatName = png ? "png" : "jpg";
		String mimeType = png ? "image/png" : "image/jpeg";
		// JPEG 는 알파를 지원하지 않으므로 RGB 로 평탄화(투명 배경 → 흰색)
		BufferedImage output = png ? image : toRgb(image);
		// 4) 재인코딩 — ImageIO 기본 라이터는 EXIF/GPS 등 부가 메타데이터를 쓰지 않는다
		byte[] bytes = encode(output, formatName);
		return new Result(bytes, formatName, mimeType);
	}

	private byte[] readAll(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}
	}

	private BufferedImage decode(byte[] source) {
		try (InputStream in = new ByteArrayInputStream(source)) {
			BufferedImage image = ImageIO.read(in);
			if (image == null) {
				throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
			}
			return image;
		} catch (IOException e) {
			throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
		}
	}

	private byte[] encode(BufferedImage image, String formatName) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(image, formatName, out)) {
				throw new BusinessException(FileErrorCode.FILE_CONTENT_INVALID);
			}
			return out.toByteArray();
		} catch (IOException e) {
			throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR);
		}
	}

	private static BufferedImage toRgb(BufferedImage image) {
		BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = rgb.createGraphics();
		try {
			g.setColor(java.awt.Color.WHITE);
			g.fillRect(0, 0, image.getWidth(), image.getHeight());
			g.drawImage(image, 0, 0, null);
		} finally {
			g.dispose();
		}
		return rgb;
	}

	private static boolean isPng(byte[] source) {
		byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
		if (source.length < png.length) {
			return false;
		}
		for (int i = 0; i < png.length; i++) {
			if (source[i] != png[i]) {
				return false;
			}
		}
		return true;
	}

	/** 디코딩 없이 헤더에서 픽셀 수(width×height)를 읽는다 — 판독 불가 시 -1. */
	private long readPixelCount(byte[] source) {
		try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
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
			log.debug("[FILE] 재인코딩 전 해상도 판독 실패 — 형식 오류로 처리", e);
			return -1L;
		}
	}

	/** 확장자(소문자) 정규화 — 출력 포맷명을 확장자로. */
	public static String normalizeExtension(String formatName) {
		return formatName == null ? "" : formatName.toLowerCase(Locale.ROOT);
	}
}
