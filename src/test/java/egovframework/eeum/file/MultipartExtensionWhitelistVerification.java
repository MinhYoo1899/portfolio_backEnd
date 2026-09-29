package egovframework.eeum.file;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 전역 멀티파트 확장자 화이트리스트({@code Globals.fileUpload.Extensions}) 회귀 가드.
 *
 * <p>EgovMultipartResolver 가 컨트롤러 도달 전(멀티파트 파싱)에 이 목록으로 확장자를 검증한다.
 * webp 가 빠지면 콘텐츠 이미지 업로드가 SecurityException(→500)으로 막힌다(CT-W1.1 회귀 방지).
 */
class MultipartExtensionWhitelistVerification {

	@Test
	@DisplayName("멀티파트 확장자 화이트리스트에 webp 포함 + 기존 이미지 확장자 유지")
	void multipartWhitelistIncludesWebpAndKeepsImages() throws IOException {
		Properties p = new Properties();
		try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
			assertNotNull(in, "application.properties 를 찾을 수 없음");
			p.load(in);
		}
		String whitelist = p.getProperty("Globals.fileUpload.Extensions", "");
		String padded = whitelist + ".";

		assertTrue(padded.contains(".webp."), "멀티파트 화이트리스트에 webp 필요(콘텐츠 업로드 게이트): " + whitelist);
		for (String ext : new String[] {"gif", "jpg", "jpeg", "png"}) {
			assertTrue(padded.contains("." + ext + "."), ext + " 확장자 유지되어야 함: " + whitelist);
		}
	}
}
