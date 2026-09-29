package egovframework.eeum.file.service.file;

/**
 * 다운로드 메타 캐리어 — 헤더 구성용 메타 + 저장키.
 *
 * <p>저장소 스트림은 여기서 열지 않는다(세션 누수 방지 — codex 코드검토). 컨트롤러가
 * {@code StreamingResponseBody} 내부에서 {@link FileService#openStream(String)} 로 지연 개방·반납한다.
 */
public record FileDownload(String orgnlFileNm, String mimeTypeNm, Long fileSz, String strgKey) {
}
