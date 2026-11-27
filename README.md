# 공통 화면 틀

Java 17 / Spring Boot 3.3.13 / Maven executable jar, blocking MyBatis 3.0.4,
PostgreSQL 16, Node 20 / React 18 / Vite 5 / npm을 사용한다.
D1의 실행 선택이며 원문 Java 21·Boot 3.5·Gradle 및 UP-001 출처 불일치를
역사적으로 승인되었거나 수정된 것으로 간주하지 않는다. 동결 명세는 변경하지 않았다.

## 시작

메뉴 API의 Controller/Service/Mapper와 공통 화면 wrapper가 연결되어 있다.
프런트는 상대 경로
GET /api/common/menus의 실제 응답만 읽고 임시 메뉴로 대체하지 않는다.

생성 단계에서는 패키지 설치·빌드·시험·서버를 실행하지 않았다.
frontend/package-lock.json이 포함되어 있다. Node 20과 npm 10으로
frontend 디렉터리에서 npm ci를 사용한다.

저장소 루트에서 docker compose -f infra/docker-compose.yml up --build를 실행한다.
UI는 http://127.0.0.1:3000, API는 http://127.0.0.1:8080 으로 연다(포트는 모든 주소에 열리며, 미리보기 검사기가 "포트:포트" 모양만 읽기 때문이다).
DB는 호스트 포트를 공개하지 않으며 Compose 내부 database 서비스로 통신한다.
Compose의 개발 DB 기본값은 로컬 시험 전용이다. 운영 배포를 별도로 결정할 때
DB_USER·DB_PASSWORD·POSTGRES_DB를 반드시 운영 설정으로 교체한다.
운영 보안·감사·백업 정책은 이 구현으로 확정하지 않는다.

## DB 준비

backend 이미지의 시작 명령은 HTTP 프로세스와 별도로
java -jar /app/application.jar --prepare-schema를 먼저 실행한다.
정상 완료한 경우에만 HTTP 애플리케이션을 실행한다. 로컬 jar 실행 시에는
환경변수 DB_URL·DB_USER·DB_PASSWORD를 지정하고 같은 준비 명령을 먼저 실행한다.
연결 정보는 코드에 하드코딩하지 않는다.

내 PC에서 jar로 돌리는 순서:

1. 만들기: backend 폴더에서 `mvn package` (jar는 backend/target/navigation-shell-1.0.0.jar).
2. 표 준비: `java -jar backend/target/navigation-shell-1.0.0.jar --prepare-schema` (정상 끝날 때만 다음으로).
3. 보통 실행: `java -jar backend/target/navigation-shell-1.0.0.jar`.

## 일 배치 (매일 02:00)

앱이 켜져 있으면 매일 02:00(한국 시각)에 배치 하나가 차례로 돈다: ① 근무 상태 ② 퇴사일 지난 개발자 사용 중지
③ 계약 종료일 지난 진행 프로젝트 종료 ④ (매달 1일만) 투입 MM·단가 ⑤ 철수 n일 전 알림 ⑥ 자금 부족 예상 알림
⑦ 30일 지난 알림 삭제 ⑧ 보관 기간 지난 접근 기록(3년)·변경 기록(프로젝트·투입·단가·원가는 영구, 그 밖 3년) 정리.
단계마다 따로 저장해 한 단계가 실패해도 다음 단계는 돌고, 결과는 서버 로그 `[일 배치]` 줄에 남는다(자동 재시도 없음).
09:00 미수 알림은 따로 돈다.

기다리지 않고 한 번 돌리기(시험·운영용, 웹 서버는 켜지 않고 끝남, HTTP API는 없음):
`java -jar backend/target/navigation-shell-1.0.0.jar --run-daily-batch` (오늘) 또는 `--run-daily-batch=2026-11-27`(그날을 기준일로).
같은 날 다시 돌려도 알림은 늘지 않는다. 단계 결과를 출력하고, 실패한 단계가 있으면 끝 코드 1.

## 환경 변수

읽는 순서: 환경 변수 > 앱이 도는 곳 옆 `config/application-local.yml`(git에 안 올림,
견본은 `config/application-local.example.yml`) > `backend/src/main/resources/application.yml` 기본값.
실제 아이디·비밀번호·키는 git에 올리지 않는다.

| 이름 | 뜻 | 기본값 |
|---|---|---|
| DB_URL | DB 주소(jdbc:postgresql://…) | 개발 PC용 값(application.yml) |
| DB_USER | DB 아이디 | 개발 PC용 값 |
| DB_PASSWORD | DB 비밀번호 | 개발 PC용 값. 운영 전에 꼭 바꾼다 |
| PAYMENT_ACCOUNT_KEY | 거래처 계좌번호 암호화 키(Base64 32바이트) | 개발용 키(application.yml). 이 값으로 켜지면 WARN 「개발용 계좌 키를 쓰고 있습니다」. 운영 전에 꼭 바꾼다 |
| SMTP_HOST | 메일 서버 주소 | smtp-relay.brevo.com |
| SMTP_PORT | 메일 서버 포트 | 587 |
| SMTP_USERNAME | 메일 아이디 | 없음 |
| SMTP_PASSWORD | 메일 비밀번호 | 없음 |
| BILLING_MAIL_FROM | 보내는 주소 | 없음(없으면 SMTP 아이디) |
| AI_API_URL·AI_API_KEY·AI_MODEL·AI_TIMEOUT_SECONDS | 외부 AI 연결(AI 분석 도우미) | 없음(키가 없으면 외부 호출 없이 규칙으로 답함) |

SMTP 비밀번호가 비어 있으면 메일을 보내지 않고 서버 임시 폴더(java.io.tmpdir)의
`billing-mail` 폴더에 .eml 파일로 남긴다. 인증번호·임시 비밀번호도 이 파일에서 읽는다(개발·시험용).
`config/application-local.yml`에서는 예전 이름 spring.mail.username·password도 읽는다.

D5에 따라 자동 Flyway는 끄고 기존 SqlSessionFactory의 한 트랜잭션에서
db/migration/V1~V6 SQL을 차례로 실행한다.
버전 자원은 jar에 포함되며 호스트 migration 디렉터리를 마운트하지 않는다.
준비 명령과 자동 Flyway를 동시에 켜지 않는다. 준비 중 PostgreSQL advisory lock을
얻고 기존 9개 대상의 컬럼·형식·precision·null·default·PK/CHECK를 모두 검사한다.
다르면 stderr에 표·컬럼·expected/actual을 출력하고 비정상 종료한다. 자동 ALTER,
삭제, 강제 확인 옵션은 없다. 사람이 외부에서 차이를 검토한 뒤 재실행해야 한다.

없는 표와 시퀀스만 생성하고 신규 물리 FK나 참조용 부모 표는 만들지 않는다.
기존 시퀀스 값은 재설정하지 않는다. 메뉴 초기값은 빈 TB_SY_MENU에서만
잠금 아래 전체 입력하고, 실패하면 준비 트랜잭션을 롤백한다. 다른 8개 표에는
초기 행을 넣지 않는다. HTTP health와 메뉴 조회는 준비 작업을 실행하지 않는다.

## 첫 로그인 — 처음 관리자 넘기기

로그인해야 화면을 쓸 수 있다(로그인 화면·로그인 API·비밀번호 찾기·인증번호·/api/health만 로그인 없이 연다).
기초 자료(sql/DML_BaseData.sql)는 처음 관리자 한 명을 넣는다:
아이디 `admin`, 이메일 `admin@example.com`(예시 주소), 역할 시스템 관리자, 임시 비밀번호 상태(TEMP_PWD_YN='Y').
처음 비밀번호는 넘겨받는 사람에게 따로 전한다(이 문서에 적지 않는다).

1. 첫 로그인 전에 관리자 이메일을 실제로 받을 수 있는 주소로 바꾼다. 안 바꾸면 인증번호를 못 받아 관리자 로그인이 안 된다.

   ```sql
   UPDATE TB_SY_USER
      SET EMAIL_ADDR = '<실제 관리자 메일 주소>', MOD_USER_ID = 'SYSTEM', MOD_DTTM = clock_timestamp()
    WHERE USER_ID = 'admin';
   ```

2. SMTP 값을 넣고 앱을 켠다(위 환경 변수 표).
3. 로그인: 아이디·비밀번호 → 메일로 온 6자리 인증번호(5분 안) → 새 비밀번호로 변경 → 첫 화면.
   2단계 인증은 시스템 관리자 역할만이며 건너뛰는 길은 없다.

안내 계약은 aiops.initial-login.json, 실행 확인 계약은 aiops.runtime-check.json이다.

## DB로 하는 복구

화면에는 계정 복구 기능이 없다. DB 운영자가 psql로 직접 실행한다(앱이 대신 실행하지 않음).
사용자 상태 USER_STAT_CD는 USE(사용)·LOCK(잠김)·STOP(중지)이다. 실제 비밀번호·해시는 문서에 적지 않는다.

잠금 해제:

```sql
UPDATE TB_SY_USER
   SET USER_STAT_CD = 'USE', PWD_ERR_CNT = 0, MFA_ERR_CNT = 0, MOD_USER_ID = 'SYSTEM', MOD_DTTM = clock_timestamp()
 WHERE USER_ID = '<아이디>';
```

이메일 바꾸기:

```sql
UPDATE TB_SY_USER
   SET EMAIL_ADDR = '<새 메일 주소>', MOD_USER_ID = 'SYSTEM', MOD_DTTM = clock_timestamp()
 WHERE USER_ID = '<아이디>';
```

임시 비밀번호: 메일이 되면 잠금을 풀고 로그인 화면 [비밀번호 찾기]나 관리자의 사용자 화면 [비밀번호 초기화]로 받는다.
메일도 관리자도 없을 때만 운영자가 만든 BCrypt(강도 10) 해시를 넣고 첫 로그인 때 바꾸게 한다.

```sql
UPDATE TB_SY_USER
   SET PWD_HASH = '<BCrypt 해시>', TEMP_PWD_YN = 'Y', PWD_ERR_CNT = 0, USER_STAT_CD = 'USE',
       MOD_USER_ID = 'SYSTEM', MOD_DTTM = clock_timestamp()
 WHERE USER_ID = '<아이디>';
```

퇴사자 계정은 관리자가 사용자 화면에서 사용 중지한다(자동 중지는 하지 않는다).

## 공통 UI 연결

frontend/src/shared/layout/AppLayout.tsx는 sidebar·title·actions·children 슬롯을 제공한다.
NavigationRoot는 실제 메뉴를 읽고 메모리 그룹 상태와 동적 주소를 연결한다.
도메인 wrapper는 NavigationRoot의 renderScreen(scrnId, menu)으로 연결할 수 있다.
단일 틀을 다시 만들거나 seed 화면 ID를 등록 판정 목록으로 사용하지 않는다.
화면별 입력·업무 버튼·사용자명·알림수는 만들지 않는다.

## 검증 명령과 현재 상태

현재 모두 not_run이다. 이 파일의 명령은 실행 결과가 아니다.

backend: mvn test. 기본 MockMvc/단위 시험에는 DB가 필요 없다.
PostgreSQL 16 통합 시험은 NAVIGATION_TEST_DB_URL과 필요 시
NAVIGATION_TEST_DB_USER·NAVIGATION_TEST_DB_PASSWORD를 지정하면 실행한다.
시험마다 무작위 전용 schema를 만들고 자기 schema만 제거한다. 설정이 없으면
이 통합 시험만 건너뛴다. 실제 DB 검증은 runner가 별도로 수행해야 한다.

frontend: npm run test -- --run, npm run typecheck, npm run build.
Vitest/jsdom은 렌더된 상태·슬롯·토글·메모리 비영속·요청 실패/복구를 검사한다.
브라우저나 외부 서버를 시험 스크립트에서 실행하지 않는다.

내 PC 시험 계정(5개 역할)은 운영 기초 자료와 따로 둔 시험 전용 SQL(localtest 표시)로 넣는다.
운영 DB에는 넣지 않는다. 시험 계정 비밀번호는 시험 계정 파일 참고.

Compose 시작 후 개발 호스트에서 python3 infra/check-loopback.py --nonloopback HOST_LAN_IP로
loopback UI/API를 확인한다. 별도 내부망 장치에서 같은 스크립트를
--remote-only --nonloopback HOST_LAN_IP로 실행해 두 포트의 연결 거부를 확인한다.
Compose 설정만으로 실제 다른 장치의 연결 불가를 검증했다고 주장하지 않는다.
GET /api/health의 성공은 {"success":true,"meta":{}}인 공개 HTTP liveness이며 DB readiness가 아니다.
PostgreSQL 가용성은 별도의 pg_isready healthcheck로 확인한다.

결정·미실행 상태와 통합 파일 확인 결과는 infra/foundation-decisions.json에 기록했다.