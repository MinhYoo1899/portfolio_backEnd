#!/usr/bin/env bash
# 새 jar 를 배포 디렉토리로 교체 후 서비스 재시작 → 헬스체크 → 실패 시 이전 버전으로 롤백.
# self-hosted runner 가 빌드 후 호출한다. (deploy.yml — testBoot deploy.sh 패턴 계승)
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/eeum}"
SERVICE="${SERVICE:-eeum-backend}"
PORT="${SERVER_PORT:-8082}"
# 배포 판정은 deploy 그룹(DB·Redis·디스크 OK)만 — 향후 인디케이터 추가에 배포기준이 흔들리지 않게(codex 교차검토)
HEALTH_URL="http://127.0.0.1:${PORT}/actuator/health/deploy"

# 빌드 산출물에서 실행 jar 찾기 — 정확히 1개여야 함 (0개/여러 개면 명시적 실패; ls+head 방식은
# set -e 에 의해 에러 메시지 전에 죽거나 엉뚱한 파일을 잡을 수 있음 — codex 교차검토 반영)
mapfile -t JARS < <(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original')
if [ "${#JARS[@]}" -ne 1 ]; then
  echo "::error::실행 jar 가 정확히 1개여야 합니다 (발견 ${#JARS[@]}개: ${JARS[*]:-없음})."
  exit 1
fi
JAR="${JARS[0]}"
echo "Deploying: ${JAR}"

rollback() {
  echo "::error::배포 실패 — 이전 버전으로 롤백합니다."
  # 실패한 신버전의 기동 로그를 Actions 로그로 노출(원격 서버 접속 없이 원인 진단). 롤백 재시작 전 시점 = 실패 앱 로그.
  echo "::group::실패 앱 기동 로그(eeum-backend 최근 200줄)"
  sudo -n journalctl -u "${SERVICE}" -n 200 --no-pager 2>/dev/null \
    || journalctl -u "${SERVICE}" -n 200 --no-pager 2>/dev/null \
    || echo "(journalctl 접근 불가 — 러너 사용자에 journal 권한 필요. 서버에서 직접 확인.)"
  echo "::endgroup::"
  if [ -f "${APP_DIR}/app.jar.bak" ]; then
    cp "${APP_DIR}/app.jar.bak" "${APP_DIR}/app.jar.new"
    mv -f "${APP_DIR}/app.jar.new" "${APP_DIR}/app.jar"
    sudo systemctl restart "${SERVICE}" || true
  else
    echo "::error::롤백할 이전 버전(app.jar.bak)이 없습니다 — 첫 배포로 보임. 서비스 상태를 수동 확인하세요."
  fi
}

# 이전 버전 백업(롤백용)
if [ -f "${APP_DIR}/app.jar" ]; then
  cp -p "${APP_DIR}/app.jar" "${APP_DIR}/app.jar.bak"
fi
# 복사를 끝까지 마친 뒤 mv(원자적 rename)로 교체 — 복사 중 중단돼도 app.jar 는 항상 온전함 (codex 교차검토 필수수정)
cp "${JAR}" "${APP_DIR}/app.jar.new"
mv -f "${APP_DIR}/app.jar.new" "${APP_DIR}/app.jar"

# 재시작 — 실패해도 롤백을 타도록 명시적으로 처리(set -e 로 조기 종료 방지)
# (runner 사용자에 NOPASSWD sudo 로 systemctl restart 허용 필요 — deploy/README.md 참고)
if ! sudo systemctl restart "${SERVICE}"; then
  rollback
  exit 1
fi

# 헬스체크 (최대 90초 — Flyway 마이그레이션 적용 시간 고려)
for i in $(seq 1 45); do
  # connect/max 타임아웃 — DB/Redis health check 가 내부 timeout 까지 오래 물려 폴링이 한두 번의 긴 curl 로 늘어지는 것 방지(codex SHOULD)
  if curl --connect-timeout 2 --max-time 5 -fsS "${HEALTH_URL}" >/dev/null 2>&1; then
    echo "Healthy ✅  배포 성공."
    rm -f "${APP_DIR}/app.jar.bak"
    exit 0
  fi
  sleep 2
done

rollback
exit 1
