# 배포 가이드 (EC2 직접 설치, Docker 미사용)

> Docker로 배포하려면 저장소의 `docker-compose.yml` 참고. 아래는 EC2에 Java/MySQL을 직접 설치하는 방식.

## 0. EC2 준비

- OS: **Ubuntu 24.04 LTS** 권장 (openjdk-21 기본 제공)
- 사양: 프리티어 t2.micro 가능 (t3.small 권장)
- **보안 그룹 인바운드**: 8080(TCP) 허용, 나중에 HTTPS 붙일 거면 80/443도. **3306은 절대 열지 말 것.**
- 키페어(.pem)로 SSH 접속: `ssh -i 키.pem ubuntu@<퍼블릭IP>`

## 1. 필수 프로그램 설치 (최초 1회)

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk-headless mysql-server git
```

프리티어(램 1GB)는 Gradle 빌드 중 메모리가 부족할 수 있으니 스왑 추가:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 2. MySQL 설정 (최초 1회)

```bash
sudo mysql
```

MySQL 콘솔에서 (비밀번호는 새로 정할 것):

```sql
CREATE DATABASE lion_apple CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'lionapple'@'localhost' IDENTIFIED BY '여기에_새_비밀번호';
GRANT ALL PRIVILEGES ON lion_apple.* TO 'lionapple'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

## 3. 코드 받기 + 빌드

```bash
git clone https://github.com/LikeLionHGU/HipApple_Backend.git
cd HipApple_Backend
./gradlew bootJar
ls build/libs   # lion-apple-0.0.1-SNAPSHOT.jar 확인
```

## 4. 환경변수 파일 작성 (최초 1회)

```bash
sudo nano /etc/hipapple.env
```

내용 (2번에서 정한 DB 비밀번호, JWT_SECRET은 `openssl rand -base64 48` 결과 등 강한 랜덤값):

```
DB_URL=jdbc:mysql://localhost:3306/lion_apple?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=lionapple
DB_PASSWORD=여기에_새_비밀번호
JWT_SECRET=여기에_32자이상_랜덤_문자열
AI_SERVER_URL=http://localhost:8000
```

권한 잠그기: `sudo chmod 600 /etc/hipapple.env`

## 5. systemd 서비스 등록 (최초 1회 — 재부팅/크래시에도 자동 재시작)

```bash
sudo nano /etc/systemd/system/hipapple.service
```

```ini
[Unit]
Description=HipApple Backend
After=network.target mysql.service

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/HipApple_Backend
EnvironmentFile=/etc/hipapple.env
ExecStart=/usr/bin/java -jar /home/ubuntu/HipApple_Backend/build/libs/lion-apple-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

실행:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now hipapple
```

## 6. 확인

```bash
sudo systemctl status hipapple          # active (running)
sudo journalctl -u hipapple -f          # 로그 실시간 보기
curl http://localhost:8080/v3/api-docs  # JSON 나오면 성공
```

외부에서: `http://<EC2 퍼블릭IP>:8080/swagger-ui.html`

## 7. Python AI 서버 + 예측 배치 (시장가격/예측 API용)

- `ai-server/` 폴더를 EC2에 두고 venv 구성: `python3.11 -m venv venv && ./venv/bin/pip install -r requirements.txt`
  (**scikit-learn은 1.9.0 고정** — v2 모델 로드 버전 일치 필요)
- `/api/price/dashboard`(구 대시보드)는 uvicorn 서버(8000 포트, systemd `hipapple-ai`)가 처리
- `/price/options`, `/price/forecast`, `/price/me`(예측 API)는 **매일 06:10 KST 배치**
  (`batch_forecast.py`, systemd `hipapple-batch.timer`)가 만든 `out/forecasts.json`을 Java가 읽어서 응답
- 예측은 ML팀 v2 파이프라인(`ai-server/ml/` — `common_hist.build_daily_ext` 피처 +
  `models/final_daily_model2.joblib`) 사용
- ⚠️ `ml/data/apple_history_raw.csv`(2021~2023 정적 히스토리, 55MB)와
  `ml/data/apple_auction_raw.csv`(현행 누적)는 git에 없음 — ML팀 배포 zip(`ml_deploy_v2.zip`)에서
  가져와 서버 `ml/data/`에 넣어야 함. 히스토리 파일은 삭제 금지(피처 기준 유지용)
- 필요한 환경변수(`/etc/hipapple.env`): `MARKET_API_KEY`(공공데이터포털 인증키),
  `FORECAST_FILE=/home/ec2-user/ai-server/out/forecasts.json`
- 배치 수동 실행: `sudo systemctl start hipapple-batch` / 로그: `journalctl -u hipapple-batch`

## 8. 프런트/구글 연동 체크리스트

- 프런트 API base URL → `http://<EC2 IP>:8080`
- 백엔드 CORS 허용 목록(`WebConfig.addCorsMappings`)에 프런트 도메인 필요
  (현재: `http://localhost:5173`, `https://hipapple-front.pages.dev`)
- Google Cloud Console "승인된 JavaScript 원본"에 프런트 주소 등록
- 프런트가 HTTPS(Cloudflare Pages 등)면 HTTP API 호출이 차단됨(mixed content)
  → 도메인 + nginx + certbot으로 백엔드 HTTPS 구성 필요 (별도 진행)

## 재배포 (코드 변경 시)

```bash
cd ~/HipApple_Backend
git pull
./gradlew bootJar
sudo systemctl restart hipapple
```
