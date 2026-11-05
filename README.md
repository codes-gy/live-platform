# live-platform

PC 화면 공유나 휴대폰 카메라로 라이브 방송을 송출하고, 다른 기기에서 실시간으로 시청하는 웹 서비스입니다.

> 작성 중입니다. 서비스 주소, 테스트 계정, 기능 확인 절차는 배포 후 채웁니다.

## 서비스 주소
- TBD

## 테스트 계정
- TBD

## 기술 스택
- Backend: Java 25, Spring Boot 4.1, Spring Security(JWT), JPA, PostgreSQL 18, Flyway
- Frontend: Next.js 16, TypeScript, Tailwind CSS
- Streaming: LiveKit (WebRTC SFU)
- Infra: AWS EC2, Docker Compose, Nginx

## 로컬 실행 방법
### 요구 사항
- JDK 25, Node.js 20+, Docker

### 환경변수
`.env.example`을 참고하세요. LiveKit 키는 https://cloud.livekit.io 에서 발급받습니다.

### 실행
```bash
docker compose up -d                  # PostgreSQL
cd backend && ./gradlew bootRun       # http://localhost:8080
cd frontend && npm install && npm run dev   # http://localhost:3000 (/api 는 BACKEND_URL=http://localhost:8080 으로 전달)
```

### 테스트
```bash
cd backend && ./gradlew test          # Testcontainers 사용, Docker 실행 필요
```

## 기능 확인 방법
- TBD (PC 방송 / 모바일 방송 / 시청 / 종료 / 권한)

## 지원 환경
- TBD

## 오픈소스 및 출처
- [LiveKit](https://github.com/livekit/livekit) (Apache-2.0): 미디어 서버, 클라이언트·서버 SDK
