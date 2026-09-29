package egovframework.eeum.file.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 업로더 1인의 최근 업로드 사용량 집계 — 쿼터 검증용(건수·누적용량).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadUsageVO {

	/** 집계 구간 내 업로드 건수. */
	private long uploadCount;

	/** 집계 구간 내 누적 용량(byte). */
	private long totalBytes;
}
