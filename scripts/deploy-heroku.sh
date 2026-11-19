#!/usr/bin/env bash
# Heroku 컨테이너 배포. 사용법: scripts/deploy-heroku.sh [api|web|all]
# 필요: docker, heroku CLI 로그인. 앱 이름은 API_APP · WEB_APP 환경 변수로 바꿀 수 있다.
set -euo pipefail

API_APP=${API_APP:-onair-live-api}
WEB_APP=${WEB_APP:-onair-live-web}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TARGET=${1:-all}

web_url() { heroku apps:info -a "$1" --json | python3 -c 'import json,sys; print(json.load(sys.stdin)["app"]["web_url"].rstrip("/"))'; }

push() { # $1=앱 $2=빌드 폴더, 나머지는 docker build 인자
  local app=$1 dir=$2; shift 2
  local image="registry.heroku.com/$app/web"
  # Heroku 다이노는 x86_64
  # Heroku 레지스트리는 Docker v2 매니페스트만 받는다(OCI 형식 · 출처 증명 첨부는 "unsupported")
  docker buildx build --platform linux/amd64 --provenance=false --sbom=false \
    --output "type=image,name=$image,push=true,oci-mediatypes=false" "$@" "$dir"
  heroku container:release web -a "$app"
}

heroku container:login

if [[ $TARGET == api || $TARGET == all ]]; then
  push "$API_APP" "$ROOT/backend"
fi
if [[ $TARGET == web || $TARGET == all ]]; then
  push "$WEB_APP" "$ROOT/frontend" --build-arg "BACKEND_URL=$(web_url "$API_APP")"
fi
