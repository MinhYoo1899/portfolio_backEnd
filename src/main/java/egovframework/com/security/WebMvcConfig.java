package egovframework.com.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

import egovframework.com.config.HtmlCharacterEscapes;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * fileName       : WebMvcConfig
 * author         : crlee
 * date           : 2023/07/13
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2023/07/13        crlee       최초 생성
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
	
	private final ObjectMapper objectMapper;
	
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(new CustomAuthenticationPrincipalResolver());
    }
    
    @Bean
    public HttpMessageConverter<?> htmlEscapingConverter() {
        ObjectMapper copy = objectMapper.copy();
        copy.getFactory().setCharacterEscapes(new HtmlCharacterEscapes());
        // non-ASCII(한글·중점(·) 등)를 \\uXXXX·HTML엔티티(&middot;)로 바꾸지 않고 UTF-8 원문으로 출력 — 콘텐츠 깨짐 방지.
        copy.getFactory().disable(com.fasterxml.jackson.core.JsonGenerator.Feature.ESCAPE_NON_ASCII);
        return new MappingJackson2HttpMessageConverter(copy);
    }
    
}