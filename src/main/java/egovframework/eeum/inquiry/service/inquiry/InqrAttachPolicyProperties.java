package egovframework.eeum.inquiry.service.inquiry;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import lombok.Getter;
import lombok.Setter;

/**
 * 문의 첨부(INQR_ATTACH) 정책 — {@code eeum.inquiry.attachment.*}.
 *
 * <p>선업로드-후연결(임시 업로드 → 문의 작성/수정 시 연결). 이미지(jpg/jpeg/png)만,
 * EXIF/GPS 제거 재인코딩 후 저장. 글 공개성이 상한선인 하이브리드 공개성(PUBLIC/PRIVATE)을 별도 컬럼으로 둔다.
 */
@Component
@ConfigurationProperties(prefix = "eeum.inquiry.attachment")
@Getter
@Setter
public class InqrAttachPolicyProperties {

	/** 첨부 1건 최대 크기. */
	private DataSize maxSize = DataSize.ofMegabytes(10);

	/** 허용 확장자(소문자, 점 없이) — 문의 첨부는 jpg/jpeg/png 만(PDF 후속). */
	private List<String> allowedExtensions = List.of("jpg", "jpeg", "png");

	/** 재인코딩(디코딩) 전 해상도 상한 — 픽셀폭탄/OOM 방어. */
	private long maxPixels = 16_000_000L;

	/** 업로더 1인당 최근 24시간 최대 업로드 건수(스코프 INQR_ATTACH 기준 — 저장소 고갈 방어). */
	private int dailyMaxCount = 50;

	/** 업로더 1인당 최근 24시간 최대 누적 용량. */
	private DataSize dailyMaxSize = DataSize.ofMegabytes(200);

	/** 문의 1건당 최대 첨부 개수(연결 시 검증). */
	private int maxPerInquiry = 5;

	/** 문의 1건당 첨부 총 용량 상한(연결 시 검증). */
	private DataSize maxTotalSizePerInquiry = DataSize.ofMegabytes(30);

	/** 임시(TEMP) 업로드 미연결 정리 TTL(시간) — 만료 후 정리잡이 soft delete. */
	private int tempTtlHours = 24;

	public long maxSizeBytes() {
		return maxSize.toBytes();
	}

	public long dailyMaxBytes() {
		return dailyMaxSize.toBytes();
	}

	public long maxTotalSizePerInquiryBytes() {
		return maxTotalSizePerInquiry.toBytes();
	}
}
