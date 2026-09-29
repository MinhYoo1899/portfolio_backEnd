# 배포 & CI/CD (개발서버)

GitHub Actions 기반 (testBoot 검증 패턴 계승). **CI**는 모든 push/PR에서 빌드+검증(`*Verification`)으로
"정상 버전"을 확인하고, **Deploy**는 `develop` push 시 개발서버(self-hosted runner)가 빌드 후
자동 재배포한다(헬스체크 실패 시 자동 롤백).

```
push (모든 브랜치) → CI(ci.yml): ./mvnw verify  (빌드+검증 게이트, GitHub-hosted)
push develop      → Deploy(deploy.yml): 러너에서 verify → deploy.sh(교체·재시작·헬스체크·롤백)
```

기존 testBoot 스택(80 nginx / 8080 Tomcat / 8081 testboot)과 완전 분리: **포트 8082,
`/opt/eeum`, `eeum-backend.service`**. (운영 서버가 생기면 v* 태그 배포 워크플로를 별도 추가)

> ⚠ **DB 마이그레이션은 배포와 분리** (2026-06-12 확정): 새 V파일(또는 R__ 시드 수정)이 포함된 변경은
> **push 전에 `scripts/db-migrate-dev.ps1 -Apply`로 개발 DB에 먼저 적용**할 것. 적용 없이 배포되면 새 버전이
> 기동 거부(validate fail)되어 헬스체크 실패 → 이전 버전으로 자동 롤백된다(서비스는 구버전 유지 — 안전).
> 반대로 DB가 코드보다 앞선 상태(구버전 앱 + 신버전 DB)는 기동 허용(확정 정책 — Flyway 기본 future 무시 유지,
> 롤백된 구버전이 정상 기동하기 위함). ⚠ 단 이 허용은 **V__ 버전 마이그레이션에 한함** — **R__(개발 시드) 수정이
> 선적용된 채 구버전으로 롤백되면 체크섬 불일치로 구버전도 기동 거부될 수 있음**(R__ 수정이 낀 배포 실패는 수동
> 복구 필요 — 드문 케이스로 수용, codex 교차검토). 서버 환경변수 `SPRING_PROFILE=dev` 필수 — 개발 시드(R__) 검사 정합.

## 서버 1회 셋업 (개발서버에서 직접 실행)

### 1) 디렉토리 (Java 17 은 설치돼 있음 — 서비스가 17 경로를 명시 사용)
```bash
sudo mkdir -p /opt/eeum/log /etc/eeum
sudo chown -R minho:minho /opt/eeum
```

### 2) 환경변수 파일 (비밀 — 커밋 금지)
```bash
sudo cp deploy/eeum.env.example /etc/eeum/eeum.env
sudo nano /etc/eeum/eeum.env          # DB_*, REDIS_*, EGOV_JWT_SECRET 등 채우기
sudo chmod 600 /etc/eeum/eeum.env
```
- `EGOV_JWT_SECRET` / `EGOV_CRYPTO_KEY` 는 서버 전용 무작위값: `openssl rand -base64 48`

### 3) systemd 서비스
```bash
sudo cp deploy/eeum-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable eeum-backend        # 첫 기동은 jar 배포(첫 워크플로 실행) 후
```

### 4) 방화벽 (외부 8082 — iptime 포트포워딩과 세트)
```bash
sudo firewall-cmd --permanent --add-port=8082/tcp
sudo firewall-cmd --reload
```

### 5) GitHub self-hosted runner 설치 (Eeum_back 저장소 전용 — testBootP 러너와 별개)
GitHub repo(Eeum_back) → Settings → Actions → Runners → "New self-hosted runner" 안내대로
**별도 디렉토리**에 설치 후 서비스 등록:
```bash
# (러너 디렉토리에서)
./config.sh --url https://github.com/parangsea/Eeum_back --token <발급토큰>
sudo ./svc.sh install && sudo ./svc.sh start
```
- ⚠ **SELinux 함정(2026-06-11 겪음)**: 러너를 **홈 디렉토리**(`~/...`)에 설치하면 svc.sh 서비스가
  `203/EXEC Permission denied` 로 실패한다 — 홈은 `user_home_t` 컨텍스트라 systemd 가 실행 불가.
  testBootP 러너처럼 **`/opt` 아래 설치가 정석**(`usr_t` 로 자동 라벨링). 홈에 이미 설치했다면:
  `sudo chcon -t bin_t <러너경로>/runsvc.sh` (단, chcon 은 전체 relabel 시 풀림 — 영구화는 `semanage fcontext` 또는 /opt 이전).
  현 서버 상태: `~/actions-runner` + chcon 적용으로 동작 중.
- 러너 실행 사용자가 `/opt/eeum` 에 쓰기 가능해야 함 (1번에서 minho 소유로 처리)
- 서비스 재시작 권한(NOPASSWD sudo) — visudo 대신 안전한 drop-in 파일:
  ```bash
  echo 'minho ALL=(ALL) NOPASSWD: /bin/systemctl restart eeum-backend, /usr/bin/systemctl restart eeum-backend' | sudo tee /etc/sudoers.d/eeum-runner
  sudo chmod 440 /etc/sudoers.d/eeum-runner
  ```

### 6) 첫 배포 & 확인
develop 에 push(또는 Actions 탭에서 deploy 재실행) →
```bash
systemctl status eeum-backend
curl -s http://127.0.0.1:8082/actuator/health    # {"status":"UP"}
journalctl -u eeum-backend -n 50 --no-pager      # 기동 로그 (Flyway 검사(validate) 통과 확인 — 적용은 수동 절차)
```
- 애플리케이션 로그 파일: `/opt/eeum/log/backend.log`

### 7) 업로드 한도 정합 (파일 첨부 — 공개 이미지 50MB)
업로드(서비스 상한 50MB)를 끝까지 받으려면 경로상의 모든 한도가 정합해야 한다. 어느 한 곳이 작으면 413(Request Entity Too Large).
- **Spring/Tomcat 멀티파트**: `SecurityConfig.multipartConfigElement()` 빈이 파일 55MB / 요청 60MB로 설정(서비스 상한 50MB 바깥 방어선). ※ 이 빈이 있으면 `spring.servlet.multipart.*` 프로퍼티는 무시된다.
- **nginx**: 8082 **앞단에 nginx를 둔 경우**에만 해당. 해당 `server`/`location` 블록에 아래 추가(8082 포트포워딩 직결이면 불필요):
  ```nginx
  client_max_body_size 60m;
  ```
  적용: `sudo nginx -t && sudo systemctl reload nginx`
- 참고: 업로드 권한은 `FILE_UPLOAD`(운영자·파트너)만, 업로더 1인 최근 24시간 쿼터(50건/500MB)·해상도 상한(50MP)으로 저장소 고갈을 방어한다.

## 파일
| 파일 | 역할 |
|---|---|
| `.github/workflows/ci.yml` | push/PR 빌드+검증 게이트 |
| `.github/workflows/deploy.yml` | develop push 시 빌드+자동배포 |
| `deploy/deploy.sh` | jar 교체·재시작·헬스체크(90초)·롤백 |
| `deploy/eeum-backend.service` | systemd 상시 실행 (Java 17 명시) |
| `deploy/eeum.env.example` | 환경변수 템플릿(비밀 제외) |
