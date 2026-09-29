package egovframework.eeum.house.service.export;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 엑셀 삽입용 썸네일 인코더 — 원본 이미지 바이트를 긴 변 {@value #MAX_EDGE_PX}px 비율유지 축소 후 PNG 로 재인코딩.
 *
 * <p>{@link egovframework.eeum.file.service.file.ImageReencoder} 의 ImageIO+Graphics2D 패턴을 참고하되,
 * 내보내기 전용(축소·PNG 고정)이라 별도 헬퍼로 둔다. 디코드 실패/깨진 이미지는 예외를 잡아 {@code null} 반환(호출 측 fallback).
 */
@Slf4j
@Component
public class ExcelThumbnailEncoder {

	/** 썸네일 긴 변 최대 픽셀. */
	static final int MAX_EDGE_PX = 160;

	/**
	 * 원본 이미지 바이트 → 긴 변 160px 비율유지 썸네일 PNG 바이트. 실패 시 {@code null}.
	 *
	 * @param source 원본 이미지 바이트(JPEG/PNG 등 ImageIO 디코딩 가능 포맷)
	 */
	public byte[] toThumbnailPng(byte[] source) {
		if (source == null || source.length == 0) {
			return null;
		}
		try {
			BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
			if (original == null) {
				return null;
			}
			BufferedImage scaled = scale(original);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(scaled, "png", out)) {
				return null;
			}
			return out.toByteArray();
		} catch (Exception e) {
			// 깨진 이미지/디코드 실패는 전체 내보내기를 막지 않음 — 해당 셀만 빈칸 fallback
			log.debug("[HOUSE_EXPORT] 썸네일 인코딩 실패 — 빈 셀로 대체", e);
			return null;
		}
	}

	private static BufferedImage scale(BufferedImage original) {
		int w = original.getWidth();
		int h = original.getHeight();
		int longEdge = Math.max(w, h);
		double ratio = longEdge <= MAX_EDGE_PX ? 1.0 : (double) MAX_EDGE_PX / longEdge;
		int tw = Math.max(1, (int) Math.round(w * ratio));
		int th = Math.max(1, (int) Math.round(h * ratio));
		BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = thumb.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(original, 0, 0, tw, th, null);
		} finally {
			g.dispose();
		}
		return thumb;
	}
}
