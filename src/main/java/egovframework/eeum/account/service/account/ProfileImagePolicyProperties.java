package egovframework.eeum.account.service.account;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import lombok.Getter;
import lombok.Setter;

/**
 * 프로필 이미지 업로드 정책 — {@code eeum.account.profile-image.*}.
 *
 * <p>공개 이미지(숙소)와 분리된 더 엄격한 정책(파일당 5MB, jpg/jpeg/png). EXIF/GPS 제거를 위해 재인코딩한다.
 */
@Component
@ConfigurationProperties(prefix = "eeum.account.profile-image")
@Getter
@Setter
public class ProfileImagePolicyProperties {

	/** 프로필 이미지 1건 최대 크기. */
	private DataSize maxSize = DataSize.ofMegabytes(5);

	/** 허용 확장자(소문자, 점 없이) — 프로필은 jpg/jpeg/png 만. */
	private List<String> allowedExtensions = List.of("jpg", "jpeg", "png");

	/** 이미지 최대 픽셀 수(width×height) — 재인코딩(디코딩) 전 픽셀폭탄 방어. 프로필은 16MP면 충분(codex Medium 6).
	 * 동시 재인코딩 폭주(다수 대용량 디코딩 → 힙/CPU 고갈) 방어용 세마포어는 백로그 — 트래픽 패턴 확인 후 도입. */
	private long maxPixels = 16_000_000L;

	/** 업로더 1인당 최근 24시간 최대 업로드 건수(저장소 고갈 방어). */
	private int dailyMaxCount = 50;

	/** 업로더 1인당 최근 24시간 최대 누적 용량. */
	private DataSize dailyMaxSize = DataSize.ofMegabytes(200);

	public long maxSizeBytes() {
		return maxSize.toBytes();
	}

	public long dailyMaxBytes() {
		return dailyMaxSize.toBytes();
	}
}
