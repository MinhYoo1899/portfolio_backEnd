package egovframework.eeum.file.service.file;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import lombok.Getter;
import lombok.Setter;

/**
 * 공개 이미지 업로드 정책 — {@code eeum.file.public-image.*}.
 * 크기 상한(서비스 레벨, 멀티파트 전역과 별개)·허용 확장자.
 */
@Component
@ConfigurationProperties(prefix = "eeum.file.public-image")
@Getter
@Setter
public class FilePolicyProperties {

	/** 공개 이미지 1건 최대 크기(예: 50MB). */
	private DataSize maxSize = DataSize.ofMegabytes(50);

	/** 허용 확장자(소문자, 점 없이). */
	private List<String> allowedExtensions = List.of("gif", "jpg", "jpeg", "png");

	/** 이미지 최대 픽셀 수(width×height) — 픽셀폭탄/디코딩 자원고갈 방어(예: 50MP). */
	private long maxPixels = 50_000_000L;

	/** 업로더 1인당 최근 24시간 최대 업로드 건수(저장소 고갈 방어). */
	private int dailyMaxCount = 50;

	/** 업로더 1인당 최근 24시간 최대 누적 용량(예: 500MB). */
	private DataSize dailyMaxSize = DataSize.ofMegabytes(500);

	public long maxSizeBytes() {
		return maxSize.toBytes();
	}

	public long dailyMaxBytes() {
		return dailyMaxSize.toBytes();
	}

	public boolean isAllowedExtension(String extLower) {
		return extLower != null && allowedExtensions.contains(extLower.toLowerCase(Locale.ROOT));
	}
}
