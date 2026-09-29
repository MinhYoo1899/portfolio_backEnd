package egovframework.eeum.idempotency;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.util.StreamUtils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * 요청 본문을 버퍼링해 재독 가능하게 하는 래퍼. 멱등 필터가 본문해시를 계산하고, 하위 컨트롤러도 같은 본문을 읽을 수 있게 한다.
 *
 * <p>생성 시 원본 InputStream 을 전량 읽어 바이트로 보관 — {@link #getInputStream()}/{@link #getReader()}는 그 버퍼를 낸다.
 */
public class CachedBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {

	private final byte[] cachedBody;

	public CachedBodyHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
		super(request);
		this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
	}

	/** 버퍼링된 원본 본문 바이트(본문해시 계산용). */
	public byte[] getCachedBody() {
		return cachedBody;
	}

	@Override
	public ServletInputStream getInputStream() {
		return new CachedServletInputStream(cachedBody);
	}

	@Override
	public BufferedReader getReader() {
		String encoding = getCharacterEncoding();
		Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
		return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody), charset));
	}

	/** 버퍼 기반 ServletInputStream — 비동기(ReadListener)는 미지원(동기 본문 재독 전용). */
	private static final class CachedServletInputStream extends ServletInputStream {

		private final ByteArrayInputStream buffer;

		CachedServletInputStream(byte[] body) {
			this.buffer = new ByteArrayInputStream(body);
		}

		@Override
		public int read() {
			return buffer.read();
		}

		@Override
		public boolean isFinished() {
			return buffer.available() == 0;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			throw new UnsupportedOperationException("비동기 본문 읽기는 지원하지 않습니다.");
		}
	}
}
