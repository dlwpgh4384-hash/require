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
UI는 http://127.0.0.1:3000, API는 http://127.0.0.1:8080에만 공개된다.
DB는 호스트 포트를 공개하지 않으며 Compose 내부 database 서비스로 통신한다.
Compose의 개발 DB 기본값은 로컬 시험 전용이다. 운영 배포를 별도로 결정할 때
DB_USER·DB_PASSWORD·POSTGRES_DB를 반드시 운영 설정으로 교체한다.
운영 보안·감사·백업 정책은 이 구현으로 확정하지 않는다.

## DB 준비

backend 이미지는 일반 Spring Boot 시작 경로에서 Flyway를 실행한다.
환경변수 DB_URL·DB_USER·DB_PASSWORD로 연결하며 연결 정보는 코드에 넣지 않는다.
db/migration/V1~V6 SQL은 jar에 포함되고 HTTP 서버 준비 전에 순서대로 적용된다.
호스트 migration 디렉터리나 별도 준비 컨테이너에 의존하지 않는다.
V6은 역할·메뉴 권한 컬럼·기본값·PK·일곱 CHECK를 검증하고 원문 MENU_ID 인덱스를 준비한다.
기존 마이그레이션은 수정하지 않는다. 구조가 다르면 자동 ALTER나 삭제 없이 시작을 거부한다.

기존 수동 준비 DB에 Flyway 이력이 없다면 자동 baseline을 켜지 않는다.
백업과 원문 구조 대조 후 운영자가 이력 도입 버전을 검토해야 하며, 비어 있지 않은 DB는
이력 도입 전 일반 시작이 거부될 수 있다. 기존 준비 경로를 일시 유지할 때만
--spring.flyway.enabled=false를 명시한다. --prepare-schema 명령도 이 옵션을 함께 지정하며,
기존 SqlSessionFactory의 한 트랜잭션에서 V1~V6을 검사·실행한다.
준비 중 PostgreSQL advisory lock을 얻고 차이가 있으면 표·컬럼·expected/actual을 보고한다.
사람이 차이를 검토한 뒤 재실행해야 한다. 일반 운영에서는 Flyway 경로만 사용한다.

없는 표와 시퀀스만 생성하고 신규 물리 FK나 참조용 부모 표는 만들지 않는다.
기존 시퀀스 값은 재설정하지 않는다. 메뉴 초기값은 빈 TB_SY_MENU에서만
잠금 아래 전체 입력하고, 실패하면 준비 트랜잭션을 롤백한다. 다른 8개 표에는
초기 행을 넣지 않는다. HTTP health와 메뉴 조회는 준비 작업을 실행하지 않는다.

## 첫 로그인

승인된 두 API는 무인증이며 로그인·관리자 계정·역할 필터는 범위 밖이다.
로그인 없이 UI를 연다. 초기 관리자나 확인용 계정을 생성하지 않는다.
안내 계약은 aiops.initial-login.json, 실행 확인 계약은 aiops.runtime-check.json이다.

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

Compose 시작 후 개발 호스트에서 python3 infra/check-loopback.py --nonloopback HOST_LAN_IP로
loopback UI/API를 확인한다. 별도 내부망 장치에서 같은 스크립트를
--remote-only --nonloopback HOST_LAN_IP로 실행해 두 포트의 연결 거부를 확인한다.
Compose 설정만으로 실제 다른 장치의 연결 불가를 검증했다고 주장하지 않는다.
GET /api/health의 성공은 {"success":true,"meta":{}}인 공개 HTTP liveness이며 DB readiness가 아니다.
PostgreSQL 가용성은 별도의 pg_isready healthcheck로 확인한다.

결정·미실행 상태와 통합 파일 확인 결과는 infra/foundation-decisions.json에 기록했다.