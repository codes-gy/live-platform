#!/usr/bin/env bash
# 루트 .env의 LiveKit 키를 운영 백엔드 앱 설정에 넣는다. 값은 화면에 출력하지 않는다.
# 사용법: scripts/heroku-livekit-config.sh [.env 경로]
# 필요: heroku CLI 로그인. 앱 이름은 API_APP 환경 변수로 바꿀 수 있다. 설정이 바뀌면 백엔드 다이노가 재시작된다.
set -euo pipefail

API_APP=${API_APP:-onair-live-api}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
ENV_FILE=${1:-$ROOT/.env}

[[ -f $ENV_FILE ]] || { echo "$ENV_FILE 이 없습니다" >&2; exit 1; }

read_var() { { grep -E "^$1=" "$ENV_FILE" || true; } | tail -n1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'; }

url=$(read_var LIVEKIT_URL)
key=$(read_var LIVEKIT_API_KEY)
secret=$(read_var LIVEKIT_API_SECRET)

for name in url key secret; do
  [[ -n ${!name} ]] || { echo "$ENV_FILE 에 LIVEKIT 값이 비어 있습니다($name)" >&2; exit 1; }
done
[[ $url == wss://* ]] || { echo "LIVEKIT_URL은 wss://로 시작해야 합니다(운영은 LiveKit Cloud)" >&2; exit 1; }

heroku config:set -a "$API_APP" LIVEKIT_URL="$url" LIVEKIT_API_KEY="$key" LIVEKIT_API_SECRET="$secret" > /dev/null
echo "$API_APP 에 LIVEKIT_URL · LIVEKIT_API_KEY · LIVEKIT_API_SECRET 를 넣었습니다"
