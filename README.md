# 공통 화면 틀 — 개발 묶음 00

Java 17 / Spring Boot 3.3.5 / Maven / MyBatis blocking / PostgreSQL 16,
React 18 / TypeScript / Vite 5 / npm을 사용한다. 원문 Gradle·Java 21·Boot 3.5 대신
생성 앱의 고정 실행 계약을 따른다. 상세 잠정 결정과 검증 한계는 docs/implementation-evidence.md에 있다.

## 시작

frontend/package-lock.json을 포함하며 Node 20·npm 10에서 npm ci로 의존성을 설치한다.
저장소 루트에서 실행:

    docker compose -f infra/docker-compose.yml config --quiet
    docker compose -f infra/docker-compose.yml build
    docker compose -f infra/docker-compose.yml up -d

브라우저: http://127.0.0.1:3000/
백엔드: http://127.0.0.1:8080/api/common/menus
상태 확인: http://127.0.0.1:8080/api/health

frontend와 backend는 loopback에만 공개하며 DB 포트는 공개하지 않는다.
기본 Compose DB는 새 전용 볼륨 menu-data를 사용한다. 개발 전용 격리 네트워크에서만
PostgreSQL trust 인증을 사용하며 비밀번호를 파일에 저장하지 않는다. 운영 배포용 구성이 아니다.
다른 컨테이너를 이 네트워크에 연결하지 말고 LAN으로 포트를 전달하지 않는다.
WSL/Windows 포트 전달도 loopback으로 유지해야 한다.

중지: docker compose -f infra/docker-compose.yml down
데이터는 named volume에 남는다. 기존 데이터 삭제 명령을 자동 실행하지 않는다.

## 첫 로그인

이번 묶음은 무인증 메뉴 틀이다. 로그인·관리자 계정·역할 검사는 생성하지 않았다.
초기 관리자 생성이나 비밀번호 입력 없이 위 브라우저 주소로 접속한다.
안내 계약: aiops.initial-login.json. 사용 확인 계약: aiops.runtime-check.json.
일반 안내의 인증 예시 경로·R09·사용자 관리 목록은 이 묶음의 기능 계약이 아니다.

## DB 준비와 기존 DB 보존

Spring Boot의 정상 시작에서 Flyway가 실행된다. Java migration은
backend/src/main/java/db/migration/에 있으며 jar의 db/migration 클래스패스에 패키징된다.
V1은 표 부재 때만 db/schema.sql을 실행한다. 기존 표에는 DDL·COMMENT·ALTER를 실행하지 않는다.
V2는 MyBatis로 메뉴 표 잠금 → 빈 표 확인 → 전체 40행 seed를 Flyway 트랜잭션 안에서 실행한다.
한 행 이상이면 아무것도 삽입하지 않는다. GET은 seed를 실행하지 않는다.
적용 후 표를 비워도 재시작 시 seed가 다시 실행되지 않는다. Flyway 이력은 삭제하지 않는다.

기존 PostgreSQL 연결은 명시적인 선택이다. DB_URL(JDBC URL), DB_USER, DB_PASSWORD를
안전한 환경 주입으로 지정한다. 호스트에서 실행할 때 SERVER_ADDRESS는 기본 127.0.0.1이다.
기존 비어 있지 않은 schema는 Flyway가 기본적으로 거부한다. 먼저 백업·schema 검토를 수행하고
메뉴만 준비할 것인지 확인해야 한다. 필요한 경우 운영자가 명시적으로
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true와 SPRING_FLYWAY_BASELINE_VERSION=0을 함께 주입한다.
이 opt-in은 V1/V2를 건너뛰지 않으며 기존 데이터는 그대로 보존한다.
기본 Compose는 기존 DB에 자동 연결하거나 자동 baseline하지 않는다.

시작 시 catalog 비교에서 TB_SY_MENU의 컬럼 형식·NOT NULL·기본값·PK·CHECK 차이를
SCHEMA_DIFFERENCE 경고로 보고한다. 조회 필수 컬럼이 없으면 시작을 중단한다.
필수 컬럼이 있으면 차이를 보고하면서 조회를 계속하며 구조 보정은 사람 확인을 기다린다.
다른 업무 표와 기존 FK는 변경하지 않는다.

## 화면과 API

미지정 주소 /는 DB 계층 정렬상 첫 표시 가능 leaf로 이동한다.
화면 주소는 /screens/뒤에 실제 SCRN_ID를 인코딩한 경로다. 공통 AppLayout 아래 30개 seed 화면이
모두 '준비 중'으로 열린다. 직접 주소·새로고침은 nginx SPA fallback을 사용한다.
비활성/비정상 화면, PC 전용 화면, 미등록 화면은 서로 다른 안내를 표시한다.
768px 미만은 휴대폰 배치이며 실제 모바일 기기 LAN 접속은 지원 범위가 아니다.
그룹과 혼합 노드는 토글만 하고, 휴대폰의 PC 전용 leaf와 빈 그룹은 숨긴다.
LOCK_YN은 이번 표시 제어에 사용하지 않는다. 화면 상태는 메모리 외에 저장하지 않는다.

GET /api/common/menus의 실제 성공 구조:
success=true, meta.screenStates, meta.parentMenuIds, data.success=true, data.meta, data.data=메뉴 배열.
이중 봉투는 제공된 OpenAPI의 참조 구조를 그대로 구현한 것이며 원문의 최상위 []와 다르다.
MenuItem에는 여덟 공개 필드만 포함한다. DB 오류는 500, success=false, meta={}이며 접속정보를 노출하지 않는다.
GET /api/health는 프로세스 liveness다. DB 준비·통신 성공은 메뉴 API와 함께 검증해야 한다.
API 파라미터·페이지·입력 폼·업무 CRUD·파일 전송·인증 API는 이번에 추가하지 않았다.
내구 OpenAPI 사본은 backend/src/test/resources/contracts/openapi.yaml이다.

## 검증 담당자 명령

기본 백엔드 시험은 외부 서비스 없이 MockMvc + 실제 메뉴 서비스 + mapper 대역으로 실행한다.

    cd backend
    mvn test

실제 PostgreSQL 시험은 TEST_DATABASE_URL, TEST_DATABASE_USER, TEST_DATABASE_PASSWORD 환경을 지정한 뒤
같은 명령으로 실행한다. 미제공 시 PostgreSqlPersistenceTest만 건너뛴다.
각 시험은 UUID schema를 소유하며 자신이 만든 schema만 정리한다. 개발 seed를 수정하지 않는다.
MenuMappingTest는 DB 없이 실제 MyBatis XML의 별칭·생성자 매핑과 모든 필드를 검사한다.

frontend/에서:

    npm ci
    npm run test -- --run
    npm run typecheck
    npm run build

Quality Agent의 개별 실행 대상도 Vitest + jsdom 컴포넌트 시험이다.
tests/e2e/menu-navigation.spec.ts와 tests/e2e/shell-states.spec.ts는 실제 App을 렌더링하고
명시적인 API 대역으로 탐색·상태·재시도를 검사하며 DB나 브라우저 서버가 필요 없다.
tests/browser/의 같은 이름 파일은 동일한 컴포넌트 시험을 재사용하는 Quality 실행 진입점이다.
두 경로 모두 Vitest 수집 대상이며 Playwright를 import하지 않는다.

    npm run test -- --run tests/e2e/menu-navigation.spec.ts
    npm run test -- --run tests/e2e/shell-states.spec.ts
    npm run test -- --run tests/browser/menu-navigation.spec.ts
    npm run test -- --run tests/browser/shell-states.spec.ts

개발 UI를 쓸 때는 API_PROXY_TARGET을 실행 중인 loopback backend origin으로 설정한 뒤 npm run dev를 쓴다.
브라우저 API 호출 자체는 항상 상대 /api 경로다. Vite preview는 loopback만 허용하며
통합 preview는 API 프록시가 있는 Compose/nginx를 사용한다.

Compose 기동 후 frontend/에서 브라우저 설치가 준비된 검증 환경의 npm run test:e2e로
tests/playwright/*.pw.ts의 별도 Playwright 시험으로 실제 API·DB 첫 화면·직접 진입·새로고침·PC 폭 왕복을 확인한다.
서버는 시험이 시작하지 않는다. jsdom 시험은 실제 DB·CSS 레이아웃 검증을 대신하지 않는다.
UI_BASE_URL은 다른 loopback preview 포트를 사용할 때만 설정한다.
DB 장애/복구·실행 중 0건·비활성 상태는 격리 DB에서 별도로 검증하고 개발 seed를 직접 삭제하지 않는다.

Docker의 backend/frontend build stage에는 시험 소스·설정이 포함된다. Dockerfile 안에서는 시험을
실행하지 않으며 최종 이미지에는 시험 도구·소스를 복사하지 않는다.
backend build 이미지에서 mvn test, frontend build 이미지에서 위 npm 검증 명령을 실행할 수 있다.
Swift/Swagger 같은 별도 문서 런타임은 추가하지 않았다.