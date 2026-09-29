package egovframework.eeum.file.service.file.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 저장소에서 연 파일 스트림 + 자원 정리 핸들.
 *
 * <p>SFTP 다운로드는 스트림을 다 읽을 때까지 채널/세션이 살아 있어야 하므로,
 * 스트림과 함께 정리 콜백을 묶어 둔다. 호출자는 스트리밍 완료 후 {@link #close()} 로
 * 스트림을 닫고 채널/세션을 반납해야 한다(자원 누수 방지 — codex 교차검토).
 */
public final class StoredResource implements Closeable {

	private final InputStream inputStream;
	private final long size;
	private final Runnable cleanup;
	private boolean closed;

	public StoredResource(InputStream inputStream, long size, Runnable cleanup) {
		this.inputStream = inputStream;
		this.size = size;
		this.cleanup = cleanup;
	}

	public InputStream inputStream() {
		return inputStream;
	}

	/** 알 수 없으면 -1. */
	public long size() {
		return size;
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		try {
			inputStream.close();
		} finally {
			if (cleanup != null) {
				cleanup.run();
			}
		}
	}
}
