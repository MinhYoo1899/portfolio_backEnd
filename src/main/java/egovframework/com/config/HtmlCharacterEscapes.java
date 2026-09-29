package egovframework.com.config;

import org.apache.commons.text.StringEscapeUtils;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;

public class HtmlCharacterEscapes extends CharacterEscapes {
	
	private static final long serialVersionUID = -6353236148390563705L;
	
	private final int[] asciiEscapes;

    public HtmlCharacterEscapes() {
        this.asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();
        // XSS 최소셋만 이스케이프(HTML 컨텍스트 태그 차단). ( ) # " ' 는 정상 콘텐츠(괄호·해시·따옴표)를 &#40; 등으로
        // 망가뜨려 제외 — JSON API 소비 SPA 는 렌더 시점에 이스케이프하므로 백엔드 과다 이스케이프 불요.
        this.asciiEscapes['<'] = CharacterEscapes.ESCAPE_CUSTOM;
        this.asciiEscapes['>'] = CharacterEscapes.ESCAPE_CUSTOM;
    }

    @Override
    public int[] getEscapeCodesForAscii() {
    	// 26.03.04 KISA 보안취약점 조치
    	// private 배열의 복제본 반환 처리
        return asciiEscapes.clone();
    }

    @Override
    public SerializableString getEscapeSequence(int ch) {
        // XSS 태그문자(< >)만 HTML 엔티티로 변환. non-ASCII(·(중점)·한글 등) 및 그 외 문자는 원문 유지 —
        // escapeHtml4 가 · 를 &middot; 로 바꾸는 과다 이스케이프 방지(ESCAPE_NON_ASCII 설정 여부와 무관하게 안전).
        if (ch == '<' || ch == '>') {
            return new SerializedString(StringEscapeUtils.escapeHtml4(Character.toString((char) ch)));
        }
        return new SerializedString(Character.toString((char) ch));
    }
}
