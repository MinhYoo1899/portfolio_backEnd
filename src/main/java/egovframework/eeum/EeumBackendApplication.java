package egovframework.eeum;

import java.util.TimeZone;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 이음 고택숙박 예약 백엔드 — 실행 진입점.
 *
 * <p>전자정부 표준프레임워크 부트 5.0.0 기반. {@code egovframework} 루트 전체를 스캔하여
 * eGov 공통 인프라({@code egovframework.com.*})와 업무 도메인({@code egovframework.eeum.*})을 함께 로드한다.
 *
 * <p>{@code @EnableScheduling}: 미연결 임시 첨부 TTL 정리 등 주기 작업을 활성화한다.
 */
@EnableScheduling
@ServletComponentScan(basePackages = "egovframework")
@SpringBootApplication(scanBasePackages = "egovframework")
public class EeumBackendApplication {

	public static void main(String[] args) {
		// 타임존 표준(docs/timezone-standard.md): JVM 전역 UTC 고정 — 발생 순간(생성/결제 등)은 UTC instant.
		// 로컬·서버 어디서 실행하든 동일 보장(OS 타임존 비의존). 숙박 "날짜"는 DATE/LocalDate(KST 비즈니스 날짜)로 별도 처리.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

		SpringApplication springApplication = new SpringApplication(EeumBackendApplication.class);
		springApplication.setBannerMode(Banner.Mode.OFF);
		springApplication.run(args);
	}
}