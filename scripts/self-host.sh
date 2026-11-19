#!/usr/bin/env bash
# 자체 호스팅 운영(docker-compose.prod.yml + Tailscale Funnel). 사용법: scripts/self-host.sh [up|url|logs|down]
#   up   : 이미지 빌드 → DB · 백엔드 · 프론트 시작 → Funnel로 127.0.0.1:3300을 HTTPS 공개 → PUBLIC_URL(CORS) 반영
#   url  : 공개 주소 출력
#   logs : 백엔드 로그
#   down : Funnel 해제 + 컨테이너 중지(DB 볼륨은 유지)
# 필요: Docker, Tailscale(로그인 · 관리 콘솔에서 Funnel 허용). Windows는 Git Bash에서 실행.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
ENV_FILE=.env.prod
PORT=3300
[[ -f $ENV_FILE ]] || { echo "$ENV_FILE 이 없습니다. .env.prod.example을 복사해 채우세요" >&2; exit 1; }
TS=$(command -v tailscale || echo "/c/Program Files/Tailscale/tailscale.exe")
dc() { docker compose -f docker-compose.prod.yml --env-file "$ENV_FILE" "$@"; }

public_url() {
  "$TS" status --json | python -c 'import json,sys; print("https://" + json.load(sys.stdin)["Self"]["DNSName"].rstrip("."))'
}

case ${1:-up} in
  up)
    url=$(public_url)
    if [[ $(grep -E '^PUBLIC_URL=' "$ENV_FILE" | cut -d= -f2-) != "$url" ]]; then
      sed -i.bak "s|^PUBLIC_URL=.*|PUBLIC_URL=$url|" "$ENV_FILE" && rm -f "$ENV_FILE.bak"
    fi
    dc up -d --build
    "$TS" funnel --bg "$PORT" > /dev/null
    echo "공개 주소: $url"
    echo "LiveKit 웹훅: $url/api/livekit/webhook"
    ;;
  url) public_url ;;
  logs) dc logs -f --tail 100 backend ;;
  down) "$TS" funnel reset || true; dc down ;;
  *) echo "사용법: $0 [up|url|logs|down]" >&2; exit 1 ;;
esac
