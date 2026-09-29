package egovframework.eeum.file.service.file.impl;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import egovframework.eeum.file.service.file.FileService;
import egovframework.eeum.file.vo.AtchFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 미연결 임시(TEMP) 첨부 TTL 정리잡 — 전 scope(PUBLIC_IMAGE/PROFILE_IMAGE/REVIEW_IMAGE/INQR_ATTACH) 공통. 1시간 주기.
 *
 * <p>업로드만 되고 끝내 어느 도메인에도 연결되지 않은 첨부(TEMP_EXPIRE_DT 경과)를 soft delete + SFTP 물리삭제(best-effort).
 * 정리 책임은 첨부를 소유한 <b>파일 도메인</b>에 둔다 — 이전엔 inquiry 도메인 잡이 {@code FILE_SCOPE_CD='INQR_ATTACH'}만
 * 정리해 나머지 scope(프로필/공개/후기) 미연결 TEMP 가 orphan 으로 영구 누적되던 갭을 닫는다(codex 교차검토 승인).
 *
 * <p>건별로 {@link FileService#cleanupExpiredTempAttachment}(프록시 경유)를 호출해 <b>건별 트랜잭션 + after-commit 물리삭제</b>를
 * 보존한다(같은 빈 내부 self-invocation 으로 묶으면 트랜잭션 프록시가 우회돼 after-commit 이 깨짐). 한 번에 처리하는
 * 건수를 제한해 장기 트랜잭션/락 점유를 피한다(다음 주기에 이어서 처리). 읽은 뒤 LINKED 되면 0건(TTL 경합 방어).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileAttachmentCleanupJob {

	/** 1회 정리 상한(다음 주기에 이어서 — 장기 트랜잭션 방지). 운영 지표 보고 조정. */
	private static final int BATCH_LIMIT = 200;

	private final FileService fileService;

	/** 1시간 주기(초기 지연 10분 — 부팅 직후 폭주 회피). */
	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
	public void cleanupExpiredTemp() {
		try {
			List<AtchFileVO> expired = fileService.selectExpiredTempAttachments(BATCH_LIMIT);
			int cleaned = 0;
			for (AtchFileVO vo : expired) {
				try {
					// 건별 프록시 호출 — cleanupExpiredTempAttachment @Transactional/after-commit 보존.
					if (fileService.cleanupExpiredTempAttachment(vo.getAtchFileSn()) > 0) {
						cleaned++;
					}
				} catch (RuntimeException e) {
					// 건별 실패는 그 건만 건너뛰고 계속 — 한 건이 뒤 건을 굶기지 않게(codex SHOULD). 다음 주기에 재시도.
					log.warn("[FILE_ATTACH] 임시첨부 정리 실패 atchFileSn={} (건너뜀)", vo.getAtchFileSn(), e);
				}
			}
			if (cleaned > 0) {
				log.info("[FILE_ATTACH] 만료 미연결 임시첨부 정리 {}건", cleaned);
			}
		} catch (RuntimeException e) {
			// 목록 조회 등 잡 자체 실패는 다음 주기에 재시도 — 부팅/서비스 흐름에 영향 주지 않게 삼킨다.
			log.warn("[FILE_ATTACH] 임시첨부 정리잡 실패(다음 주기 재시도)", e);
		}
	}
}
