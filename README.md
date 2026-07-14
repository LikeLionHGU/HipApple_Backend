# Lion Apple Backend

Spring Boot로 구현한 API 명세서 기반 백엔드입니다.

## 실행

```bash
gradle bootRun
```

서버 기본 포트는 `8080`입니다.

Gradle은 Java 21로 실행해야 합니다. IntelliJ에서 빌드할 때 `Unsupported class file major version 66` 오류가 나면 `Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM`을 Java 21로 바꾸세요.

Swagger UI는 서버 실행 후 아래 주소에서 확인할 수 있습니다.

```text
http://localhost:8080/swagger-ui.html
```

Google OAuth Client ID는 `application.yml`에 설정되어 있습니다. Client Secret은 저장소에 직접 커밋하지 않고 환경변수로 주입합니다.

```powershell
$env:GOOGLE_CLIENT_SECRET="your-google-client-secret"
$env:JWT_SECRET="your-long-random-jwt-secret"
```

MySQL 데이터베이스는 기본값으로 `localhost:3306/lion_apple`을 사용합니다.

```sql
CREATE DATABASE lion_apple CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

DB 계정 정보는 [application.yml](src/main/resources/application.yml)에서 수정하면 됩니다.

## 구현된 API

### User

| Method | Endpoint | 설명 | 구현 응답 |
| --- | --- | --- | --- |
| POST | `/user/google` | Google ID Token 검증 후 로그인 | `{ "accessToken": "jwt..." }` |
| POST | `/user/profile` | 농가 정보 입력 | `{ "result": "Success" }` |
| GET | `/user/me` | 사용자 정보 조회 | `{ "id": 1, "name": "박주아" }` |

## 구글 로그인 요청 예시

```json
{
  "idToken": "google-id-token-from-frontend"
}
```

`idToken`은 프런트에서 Google 로그인 성공 후 받은 `response.credential` 값을 전달합니다. 백엔드는 Google ID Token의 서명, audience, issuer, 만료시간을 검증한 뒤 우리 서비스 JWT accessToken을 발급합니다.

### Storage

| Method | Endpoint | 설명 | 구현 응답 |
| --- | --- | --- | --- |
| POST | `/storage` | 저장고 등록 | `{ "result": "Success" }` |
| GET | `/storage` | 전체 저장고 조회 | 저장고 요약 목록 |
| GET | `/storage/{storageId}` | 세부적인 저장고 조회 | 온도, 습도, 에틸렌, 저장기간, 품질상태, 출하 추천일, 분석근거, 주변 날짜 포함 |
| PUT | `/storage/{storageId}` | 저장고 수정 | `{ "result": "Success" }` |
| DELETE | `/storage/{storageId}` | 저장고 삭제 | `{ "result": "삭제 완료" }` |

## 저장고 등록/수정 요청 예시

```json
{
  "name": "저장고A",
  "appleType": "부사 시스코",
  "storeDate": "2026-07-01T00:00:00",
  "storageMethod": "CA",
  "brix": 15,
  "hardness": 10,
  "condition": "우수",
  "amount": 5,
  "preferredDate": "12월 중순"
}
```

저장고 데이터는 MySQL에 저장됩니다. `spring.jpa.hibernate.ddl-auto=update`로 설정되어 있어 애플리케이션 실행 시 필요한 테이블이 자동 생성/갱신됩니다.
