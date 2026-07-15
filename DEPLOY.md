# 배포 가이드 (EC2 + Docker)

## 0. 사전 준비

- AWS 계정으로 EC2 인스턴스 생성
  - OS: Ubuntu 22.04 이상
  - 사양: 프리티어 t2.micro 가능 (t3.small 권장)
  - **보안 그룹 인바운드**: 8080(TCP), 80/443(나중에 HTTPS용) 허용. **3306은 절대 열지 말 것.**
- 키페어(.pem)로 SSH 접속

## 1. 서버에 Docker 설치 (최초 1회)

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER
# 로그아웃 후 재접속 (그룹 반영)
```

프리티어(램 1GB)라면 빌드 중 메모리 부족 방지용 스왑 추가:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
```

## 2. 코드 받기 + 환경변수 설정

```bash
git clone https://github.com/LikeLionHGU/HipApple_Backend.git
cd HipApple_Backend
cp .env.example .env
nano .env   # DB_PASSWORD, JWT_SECRET을 새로운 강한 값으로 채우기
```

- `JWT_SECRET`은 32자 이상 아무 랜덤 문자열 (예: `openssl rand -base64 48` 결과)
- `DB_PASSWORD`는 새로 정하는 값 (docker MySQL이 이 값으로 초기화됨)

## 3. 실행

```bash
docker compose up -d --build
```

확인:

```bash
docker compose ps                 # app, db 둘 다 running/healthy
curl http://localhost:8080/v3/api-docs   # JSON 나오면 성공
```

외부에서: `http://<EC2 퍼블릭 IP>:8080/swagger-ui.html`

## 4. Python AI 서버 (시장가격 API용)

`/api/price/dashboard`는 Python 서버(기본 8000 포트)로 프록시한다.
같은 EC2에서 Python 서버를 8000 포트로 실행해두면 기본 설정으로 동작.
다른 곳에 있으면 `.env`의 `AI_SERVER_URL`을 그 주소로 변경 후 `docker compose up -d` 재실행.

## 5. 프런트/구글 연동 체크리스트

- 프런트 API base URL → `http://<EC2 IP>:8080`
- 백엔드 CORS 허용 목록(`WebConfig.addCorsMappings`)에 프런트 도메인이 있어야 함
  (현재: `http://localhost:5173`, `https://hipapple-front.pages.dev`)
- Google Cloud Console "승인된 JavaScript 원본"에 프런트 주소 등록
- 프런트가 HTTPS(예: Cloudflare Pages)면 브라우저가 HTTP API 호출을 차단함(mixed content)
  → 도메인 연결 + nginx + certbot으로 백엔드 HTTPS 구성 필요 (별도 진행)

## 재배포 (코드 변경 시)

```bash
git pull
docker compose up -d --build
```

## 로그 보기 / 중지

```bash
docker compose logs -f app   # 앱 로그
docker compose down          # 중지 (DB 데이터는 volume에 유지됨)
```
