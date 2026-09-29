package egovframework.com.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 부팅 전략 — 검사(validate)만 수행하고, 적용(migrate)은 절대 하지 않는다.
 *
 * <p>팀 룰(2026-06-12 확정): <b>앱 구동과 DB 변경의 분리</b> — 앱을 켜는 행위가 DB 를 바꾸면
 * 안 된다(공유 개발 DB 를 전원이 바라보는 구조에서 부팅 시 자동 적용은 위험). 마이그레이션
 * 적용은 수동 명령으로만 한다: {@code scripts/db-migrate-dev.ps1} 또는
 * {@code mvnw -Pmigrate-dev flyway:migrate}. (docs/flyway-guide.md 방식 B)
 *
 * <p>미적용 마이그레이션(pending)이나 체크섬 불일치가 있으면 validate 예외로 <b>기동이
 * 거부</b>된다(fail fast) — 코드와 DB 가 어긋난 채 서비스되는 것을 막는다. 이 경우 위의
 * 수동 적용을 먼저 실행한 뒤 다시 기동할 것.
 */
@Configuration
public class FlywayValidateOnlyConfig {

	@Bean
	public FlywayMigrationStrategy validateOnlyMigrationStrategy() {
		return flyway -> flyway.validate();
	}
}
