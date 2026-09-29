package egovframework.com.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger / OpenAPI 설정 (이음 고택숙박 예약 API).
 * 인증: Authorization 헤더에 JWT 전달(전자정부 JwtAuthenticationFilter 연동).
 */
@Configuration
public class OpenApiConfig {

	private static final String API_NAME = "이음 고택숙박 예약 API";
	private static final String API_VERSION = "0.0.1";
	private static final String API_DESCRIPTION = "이음 고택(한옥)숙박 예약 서비스 백엔드 API 명세서";

	@Bean
	public OpenAPI api() {
		return new OpenAPI()
				.info(new Info().title(API_NAME)
						.description(API_DESCRIPTION)
						.version(API_VERSION)
						.license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
				.components(new Components()
						.addSecuritySchemes("Authorization", new SecurityScheme()
								.name("Authorization")
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.HEADER)));
	}
}
