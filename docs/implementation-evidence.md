# 구현 및 검증 인계

## 구현 범위

공통 화면 틀 한 개와 DB 메뉴 목록 API를 구현했다. Java 17, Spring Boot 3.3.5,
Maven executable boot jar, MyBatis blocking, PostgreSQL 16, React 18, Vite 5,
TypeScript, npm을 사용한다. 업무 CRUD·인증·첨부·감사·백업 API는 이번 범위가 아니다.
원문 및 tasks의 Gradle 지시보다 생성 앱의 명시적인 Maven 계약을 우선했다.

## UI Contract 체크리스트와 구현 연결

- SCR-COMMON / 및 /screens/:scrnId: 하나의 AppLayout·Header·Sidebar·본문.
- 목표/archetype: DB 메뉴 계층 탐색과 선택 화면의 미구현 안내. 업무 폼·표·카드는 만들지 않는다.
- 메뉴 이름·순서·화면 ID·아이콘 이름은 GET /api/common/menus의 DB 조회값을 사용한다.
- 일반/혼합 그룹 CTA는 토글만 수행하고 본문을 유지한다. leaf CTA는 실제 scrnId로 이동한다.
- 첫 진입은 표시 가능한 DB 첫 leaf, 미등록 주소는 자동 이동 없이 첫 화면 CTA를 제공한다.
- loading·error/재시도·empty·success를 구분한다. 내부 오류문은 표시하지 않는다.
- 비활성/비정상 주소와 PC 전용 주소와 미등록 주소는 각각 다른 본문 안내를 제공한다.
- 휴대폰 폭에서 PC 전용 leaf 및 빈 그룹은 숨기며 PC 폭 복원 시 기존 선택 본문을 복원한다.
- 입력/편집/파괴 CTA 및 API payload는 없다. 메뉴/화면 상태는 메모리에만 둔다.
- 즐겨찾기·최근 화면·탭·도구줄·단축키·알림은 준비 중 disabled 자리만 제공한다.
- 시각 매핑: 조밀한 탐색 영역, 56px 헤더, 240px 메뉴, 24px 본문 여백,
  청색 primary·밝은 배경·흰 표면·구분선 토큰. 브라우저 시각 대조는 미실행이다.

## 영속성과 마이그레이션

스키마 SQL을 먼저 만들고 mapper와 application layers를 작성했다. 내구 자원은
backend/src/main/resources/db/schema.sql 및 menu-seed.sql이다. 메뉴 표만 생성하며
참조 설명을 물리 FK로 바꾸지 않는다. 기존 업무 SQL 파일은 수정하지 않았다.

Flyway Java migration은 표 존재 시 비파괴 catalog 비교, 표 부재 시 SQL 실행,
일회 seed는 MyBatis 잠금·빈 표 검사·삽입을 Flyway 트랜잭션 안에서 수행한다.
Java migration은 src/main/java/db/migration에 두어 컴파일된 classpath:db/migration으로
패키징한다. 따라서 SQL version migration을 resources/db/migration에 별도로 중복 생성하지 않았다.
기존 DB opt-in 및 baseline 버전 0 안내는 README에 있다. 자동 baseline은 꺼져 있다.

MenuMapper.xml은 quoted camelCase SQL labels와 명시적 record constructor resultMap을 사용한다.
MenuMappingTest는 DB 없이 이 실제 resultMap·labels·constructor 필드를 대조한다.
PostgreSqlPersistenceTest는 각 시험이 UUID schema를 만들고 자신이 만든 schema만 정리한다.
실제 PostgreSQL 호환성·트랜잭션·재시작 내구성은 실행하여 입증하지 않았다.

## 실제 수행한 확인

- docker compose -f infra/docker-compose.yml config --quiet: exit 0.
- git diff --check: exit 0.
- tree-sitter syntax parse: Java 17개 파일, TypeScript 7개 파일, TSX 7개 파일 오류 없음.
- JSON/XML/YAML 파싱, 내구 OpenAPI operationId와 runtime-check method/path 대조: 통과.
- Compose 앱 port·image/build·pull_policy, DB host port 미노출,
  각 context .dockerignore 필수 제외 항목과 Dockerfile USER 지시문 확인: 통과.
- 생성 backend/frontend의 입력 디렉터리 참조 부재 및 trailing whitespace 확인: 통과.
- 내구 seed의 INSERT 데이터 행: 40행 확인. 전체 값의 실 DB 대조는 미실행.
- npm registry에서 Vite 5.4.21 존재와 engine 조건 확인. 설치는 하지 않았다.

위 검사는 컴파일·시험·실행 성공을 뜻하지 않는다.

## 실행 차단 및 후속 검증

생성 단계에서 패키지 매니저·mvn test·dev server를 실행하지 말라는 지시에 따라
의존성 설치와 시험은 실행하지 않았다. frontend/package-lock.json은 없으며 임의 작성하지 않았다.
후속 검증 단계에서 npm으로 materialize해야 npm ci와 frontend Docker 빌드가 가능하다.
호스트 Node는 v22.21.1로 확인되어 계약의 Node 20 실행 증거가 아니다.
java/javac/mvn은 PATH 탐색에서 발견되지 않았다. Docker Compose v2.27.0은 있으나
Docker 데몬은 unix:///var/run/docker.sock 연결 불가였다.

JUnit/MockMvc, MyBatis mapping 시험, Vitest/jsdom, PostgreSQL 시험, Playwright는 모두 미실행이다.
PostgreSQL 환경이 제공되지 않아 선택적 통합 시험의 실행 결과도 없다.
API 실제 응답·DB 장애/복구·컨테이너 build/up·브라우저 화면·레퍼런스 시각 대조는 확인하지 못했다.
Playwright는 실 API 기반 직접 진입·폭 왕복·미등록 복귀·저장소 경계를 작성했지만
격리 DB 변경을 동반하는 모든 US1~US9 E2E 시나리오가 완성된 것은 아니다.
동시 seed 실패/rollback, 경고 로그의 전체 조합, 전후 DB fingerprint 및 모든 원문 seed 값 비교도
후속 실 PostgreSQL 검증이 필요하다. T001~T038 전체를 완료/통과로 선언하지 않는다.

## 잠정 결정과 입력 차이

미정 사항은 추천안으로 구현했으며 사람 승인으로 표시하지 않는다. 지정 call_id의
outbox JSON에 질문·선택지·근거·provisional_pending 상태를 기록했다.

- 이중 응답 봉투: 현 OpenAPI 참조 구조를 따라 data.data 배열을 사용한다.
- meta 확장: screenStates와 원본 parentMenuIds로 비활성 주소·혼합 노드를 판정한다.
- URL·폭·시각 토큰은 잠정 기본 선택이며 DB 식별자를 변경하지 않는다.
- 로컬 preview DB는 전용 볼륨, host port 없음, trust 인증이다. 개발 격리 전용이며 운영용이 아니다.
- 입력 OpenAPI에는 없는 operationId·GET health 계약을 내구 시험 사본에 추가하고,
  미사용 securitySchemes 및 빈 security를 제거했다. 입력 원본은 변경하지 않았다.
  따라서 runtime-check는 원본과 완전 일치하는 것으로 주장할 수 없으며 이 차이의 계약 승인이 필요하다.
- 인증 예시의 사용자/역할 API는 제품 명세의 제외 범위여서 구현하지 않았다.

## 변경 파일 목록

.gitignore
README.md
aiops.runtime-check.json
aiops.initial-login.json
backend/.dockerignore
backend/Dockerfile
backend/pom.xml
backend/src/main/resources/application.yml
backend/src/main/resources/db/schema.sql
backend/src/main/resources/db/menu-seed.sql
backend/src/main/resources/mappers/MenuMapper.xml
backend/src/main/java/db/migration/V1__Menu_schema.java
backend/src/main/java/db/migration/V2__Initial_menu.java
backend/src/main/java/com/cost/shell/ShellApplication.java
backend/src/main/java/com/cost/shell/MapperConfiguration.java
backend/src/main/java/com/cost/shell/HealthController.java
backend/src/main/java/com/cost/shell/ApiErrors.java
backend/src/main/java/com/cost/shell/db/MenuSchemaInspector.java
backend/src/main/java/com/cost/shell/db/SchemaCheck.java
backend/src/main/java/com/cost/shell/menu/MenuController.java
backend/src/main/java/com/cost/shell/menu/MenuService.java
backend/src/main/java/com/cost/shell/menu/MenuMapper.java
backend/src/main/java/com/cost/shell/menu/MenuGraph.java
backend/src/main/java/com/cost/shell/menu/MenuRow.java
backend/src/main/java/com/cost/shell/menu/MenuItem.java
backend/src/test/java/com/cost/shell/MenuHttpTest.java
backend/src/test/java/com/cost/shell/MenuMappingTest.java
backend/src/test/java/com/cost/shell/PostgreSqlPersistenceTest.java
backend/src/test/resources/contracts/openapi.yaml
frontend/.dockerignore
frontend/Dockerfile
frontend/nginx.conf
frontend/index.html
frontend/package.json
frontend/tsconfig.json
frontend/vite.config.ts
frontend/playwright.config.ts
frontend/src/main.tsx
frontend/src/App.tsx
frontend/src/styles.css
frontend/src/useMobile.ts
frontend/src/menuTree.ts
frontend/src/services/menus.ts
frontend/src/components/AppLayout.tsx
frontend/src/components/Header.tsx
frontend/src/components/Sidebar.tsx
frontend/src/pages/ScreenOutlet.tsx
frontend/tests/setup.ts
frontend/tests/layout.test.tsx
frontend/tests/e2e/menu-navigation.spec.ts
infra/docker-compose.yml
docs/implementation-evidence.md
.aiops-code-context/outbox/review.json
.aiops-code-context/outbox/review.map.json
.aiops-code-context/outbox/dd74ce8e40ef4c07aed86329ba217c8e.json

테스트 자원 중 OpenAPI는 기존 runner 사본을 수정했다. 기타 나열한 앱 파일은 신규 생성이다.
AGENTS.md 및 입력 문서는 수정하지 않았고 commit/push는 하지 않았다.
