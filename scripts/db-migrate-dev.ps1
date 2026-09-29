# =============================================================================
# 개발 DB 마이그레이션 수동 적용 (팀 룰 2026-06-12: 앱 구동과 DB 변경의 분리)
#
# 사용:
#   .\scripts\db-migrate-dev.ps1          → 적용 "예정분 확인"만 (flyway:info — DB 변경 없음)
#   .\scripts\db-migrate-dev.ps1 -Apply   → 실제 적용 (flyway:migrate)
#
# 동작: .env.develop 의 DB_* 접속정보를 이 프로세스에만 로드한 뒤
#       Maven 프로파일 migrate-dev(pom.xml)로 Flyway 를 실행한다.
# ⚠ 개발 DB 전용 — 개발 시드(db/migration-dev)가 포함되므로 운영 DB에 사용 금지.
# =============================================================================
param([switch]$Apply)
$ErrorActionPreference = "Stop"

$root = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $root ".env.develop"

# --- 가드 1: .env.develop 존재 확인 (codex 교차검토 필수수정 — 엉뚱한 DB 오적용 방지) ---
if (-not (Test-Path $envFile)) {
    Write-Error ".env.develop 이 없습니다 ($envFile) — 개발 DB 접속정보 없이 실행할 수 없습니다."
}

# --- 가드 2: 세션에 남아있던 DB_* 값 제거 후, .env.develop 값만 로드 ---
"DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD" | ForEach-Object {
    Remove-Item -Path "env:$_" -ErrorAction SilentlyContinue
}
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*(DB_[A-Z_]+)=(.*)$') {
        Set-Item -Path "env:$($Matches[1])" -Value $Matches[2].Trim()
    }
}

# --- 가드 3: 필수 키 전부 채워졌는지 확인 ---
$missing = @("DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD") |
    Where-Object { [string]::IsNullOrWhiteSpace((Get-Item "env:$_" -ErrorAction SilentlyContinue).Value) }
if ($missing) {
    Write-Error ".env.develop 에 누락된 값: $($missing -join ', ')"
}

$goal = if ($Apply) { "flyway:migrate" } else { "flyway:info" }
Write-Host "== $goal (대상: $env:DB_HOST`:$env:DB_PORT/$env:DB_NAME) =="

# locations 가 filesystem 상대경로라 반드시 repo 루트에서 실행
Push-Location $root
try {
    & .\mvnw.cmd -B -Pmigrate-dev $goal
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
