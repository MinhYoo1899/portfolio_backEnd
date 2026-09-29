package egovframework.eeum.reservation.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 운영자 예약 콘솔 목록 검색(전부 선택). status(예약상태코드)/기간(체크인)/keyword 필터 + 페이지네이션(기본 10).
 * ★예약경로(channel) 필터는 스키마 부재로 미지원(요청에 없음).
 */
@Schema(description = "운영자 예약 목록 검색")
public record OperatorReservationSearchRequest(

		@Schema(description = "예약상태코드 필터(RESERVED/USED/CANCELLED 등, null=전체)") @Size(max = 20) String status,

		@Schema(description = "체크인 시작일(KST, 이상)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

		@Schema(description = "체크인 종료일(KST, 이하)") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

		@Schema(description = "검색어(고택명/예약자명/주문번호, null=전체)") @Size(max = 200) String keyword,

		@Schema(description = "페이지(1-base, 기본 1)") @Min(1) Integer page,

		@Schema(description = "페이지 크기(기본 10, 최대 50)") @Min(1) @Max(50) Integer size) {

	public int pageOrDefault() {
		return page == null ? 1 : page;
	}

	public int sizeOrDefault() {
		return size == null ? 10 : size;
	}

	public int offset() {
		return (pageOrDefault() - 1) * sizeOrDefault();
	}
}
