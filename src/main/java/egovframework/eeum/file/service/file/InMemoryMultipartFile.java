package egovframework.eeum.file.service.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

/**
 * 메모리 보유 {@link MultipartFile} — 재인코딩 결과 등 서버가 가공한 바이트를 공통 저장 로직에 넘길 때 사용.
 *
 * <p>{@code FileService.storeImage} 가 MultipartFile 계약(파일명/스트림/크기)만 의존하므로,
 * 가공 바이트를 원본 요청과 동일한 인터페이스로 흘려보내기 위한 경량 어댑터다.
 */
public final class InMemoryMultipartFile implements MultipartFile {

	private final String name;
	private final String originalFilename;
	private final String contentType;
	private final byte[] content;

	public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
		this.name = name;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.content = content;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getOriginalFilename() {
		return originalFilename;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public boolean isEmpty() {
		return content == null || content.length == 0;
	}

	@Override
	public long getSize() {
		return content == null ? 0 : content.length;
	}

	@Override
	public byte[] getBytes() {
		return content;
	}

	@Override
	public InputStream getInputStream() {
		return new ByteArrayInputStream(content);
	}

	@Override
	public void transferTo(Path dest) throws IOException {
		Files.write(dest, content);
	}

	@Override
	public void transferTo(java.io.File dest) throws IOException {
		Files.write(dest.toPath(), content);
	}
}
