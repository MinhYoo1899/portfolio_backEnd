package egovframework.eeum.reservation.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예약 게스트 VO — RESERVATION_GUEST 매핑(PII 분리).
 *
 * <p>{@code guestNm} 은 이름(암호화 제외 대상)이라 평문, {@code guestMblTelno} 는 AES-GCM 암호문
 * (매퍼에서 {@code GuestMblTelnoEncryptedTypeHandler} 로 바인딩). 응답 DTO 로는 절대 노출하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationGuestVO {

	/** 예약게스트일련번호(PK). */
	private Long reservationGuestSn;

	/** 예약일련번호. */
	private Long reservationSn;

	/** 게스트유형코드(RESERVER=예약자/OCCUPANT=투숙자). */
	private String guestTypeCd;

	/** 게스트명(평문 — 암호화 제외 대상). */
	private String guestNm;

	/** 게스트 휴대전화번호(암호문 — TypeHandler 바인딩). */
	private String guestMblTelno;

	/** 개인정보 파기예정일시(UTC). */
	private LocalDateTime piiPurgeDueDt;

	/** 등록일시. */
	private LocalDateTime regDt;

	/** 등록자(행위자). */
	private Long rgtrSn;

	/** 수정일시. */
	private LocalDateTime mdfcnDt;

	/** 수정자(행위자). */
	private Long mdfrSn;

	/** 삭제여부. */
	private String delYn;
}
