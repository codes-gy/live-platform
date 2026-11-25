# 운영 DB는 Heroku 애드온이 아니라 앱 밖의 Neon PostgreSQL에 둔다

11-14에 Heroku Postgres 애드온이 앱에서 분리 · 삭제되어 운영이 멈췄다. 앱을 정리하거나 다시 만들어도 데이터가 남도록 DB를 앱 수명과 떼어 Neon(Heroku와 같은 AWS us-east-1)에 두고 `DB_URL` · `DB_USERNAME` · `DB_PASSWORD`로 직접 연결한다. 무료 플랜이 5분 유휴 뒤 잠들어 첫 요청이 1초 안팎 늦는 것은 받아들였다.

근거: 커밋 fc8021b, PR #37(#14), `backend/docker-entrypoint.sh`(`DB_URL`이 있으면 `DATABASE_URL` 변환 생략), [docs/DEPLOY.md](../DEPLOY.md) 1절 · [docs/ARCHITECTURE.md](../ARCHITECTURE.md) 5절.
