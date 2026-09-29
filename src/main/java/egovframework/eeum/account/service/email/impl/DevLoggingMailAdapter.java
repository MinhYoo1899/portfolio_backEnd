package egovframework.eeum.account.service.email.impl;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import egovframework.eeum.account.service.email.EmailVerificationMailCommand;
import egovframework.eeum.account.service.email.EmailVerificationMailPort;
import lombok.extern.slf4j.Slf4j;

/**
 * dev 더미 발송 어댑터 — 실제 메일을 보내지 않고 콘솔(로그)에만 인증코드를 출력한다.
 *
 * <p><b>비운영 프로파일 전용</b>({@code (local | dev) & !prod}): 평문 코드를 로그에 남기는 것은 개발 편의를 위함이다.
 * {@code "default"}(프로파일 미지정)는 의도적으로 제외한다 — 운영에서 {@code SPRING_PROFILE} 을 빠뜨려도
 * 빈 기본 프로파일에서는 이 빈이 뜨지 않아, 실제 발송 어댑터(SMTP/SES — 미구현)가 없으면
 * {@code EmailVerificationMailPort} 주입이 실패해 <b>fail-fast</b> 된다(운영에서 코드가 로그로 새는 사고 차단 — codex MUST).
 * {@code !prod} 를 명시해 {@code prod,dev} 처럼 운영+개발 프로파일이 동시에 활성화돼도 이 더미가 뜨지 않게 한다(codex MUST).
 * 개발/로컬은 {@code SPRING_PROFILE=dev}(.env.develop) 로 활성화한다.
 */
@Slf4j
@Profile("(local | dev) & !prod")
@Component
public class DevLoggingMailAdapter implements EmailVerificationMailPort {

	@Override
	public void sendVerificationCode(EmailVerificationMailCommand cmd) {
		// dev 전용 — 운영 비활성(평문 코드 로그 금지). 실제 발송 어댑터로 교체 필요.
		log.info("[EMAIL-VERIFY][DEV] to={} code={} ttlSeconds={} (dev 더미 — 실제 메일 미발송)",
				cmd.toEmail(), cmd.code(), cmd.ttlSeconds());
	}
}
