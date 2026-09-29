package egovframework.eeum.file.service.file;

import java.util.List;
import java.util.Locale;

/**
 * 이미지 저장 스코프별 정책 — 공통 저장 로직({@code FileService.storeImage})에 주입.
 *
 * <p>스코프(PUBLIC_IMAGE/PROFILE_IMAGE 등)마다 크기·확장자·픽셀 상한·쿼터·저장키 접두사가 다르므로,
 * 정책을 값으로 분리해 동일한 검증·저장·메타 등록 흐름을 재사용한다.
 * 재인코딩(EXIF 제거)은 호출 측에서 선처리하므로 본 정책에는 포함하지 않는다.
 *
 * @param fileScopeCd       파일공개범위코드(ATCH_FILE.FILE_SCOPE_CD — 예: PUBLIC_IMAGE/PROFILE_IMAGE)
 * @param storageKeyPrefix  저장키 접두사(예: "public", "profile") — base 미포함 상대키 1번째 세그먼트
 * @param maxSizeBytes      1건 최대 크기(byte)
 * @param allowedExtensions 허용 확장자(소문자, 점 없이)
 * @param maxPixels         최대 픽셀 수(width×height — 픽셀폭탄 방어)
 * @param dailyMaxCount     업로더 1인당 최근 24시간 최대 건수
 * @param dailyMaxBytes     업로더 1인당 최근 24시간 최대 누적 용량(byte)
 */
public record ImageStorePolicy(
		String fileScopeCd,
		String storageKeyPrefix,
		long maxSizeBytes,
		List<String> allowedExtensions,
		long maxPixels,
		int dailyMaxCount,
		long dailyMaxBytes) {

	public boolean isAllowedExtension(String extLower) {
		return extLower != null && allowedExtensions.contains(extLower.toLowerCase(Locale.ROOT));
	}
}
