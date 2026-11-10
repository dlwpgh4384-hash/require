# UI 개선 계약 체크리스트

## 근거와 공통 계약

- 우선 근거: 직접 방문한 sync.html의 실제 DOM/CSS 및 Chromium 데스크톱/모바일 렌더. 보조 근거: .aiops-spec/ui-style.md.
- 사용자 기능 정본: .aiops-spec/ui-design.md. 전 화면 archetype=placeholder-navigation, 입력폼·검색·업무 CRUD 없음.
- 조회 연결: NavigationRoot → GET /api/common/menus → CommonMenuController → CommonMenuService → MenuRules → MenuMapper.selectCandidates → PostgreSQL TB_SY_MENU.
- 실제 봉투: MenuResponse의 success/data/meta.screenAccess. menuNm/upperMenuId/scrnId/iconNm/pcOnlyYn을 그대로 소비한다. 가시 트리 부재만으로 미등록을 추측하지 않는다.
- CTA: 부모 토글, leaf route 이동, 실패 재조회, 미등록 시 첫 가시 화면 이동. 저장·삭제·확인 모달·payload는 모든 화면 해당 없음. GET에는 query/body 없음.
- validation: HTTP/JSON/봉투 실패는 양쪽 오류 및 다시 시도. loading 중 기존 본문 숨김. 정상 0건은 빈 안내.
- 성공: 선택한 동일 route에 DB 메뉴명과 준비 중 표시. 모바일 메뉴 선택 후 패널 닫힘. 브라우저 주소 직접 진입·뒤로/앞으로 지원.
- constraints: 미등록 → 사용불가 → 모바일 PC전용 → 준비 중 순서. 역할 필터·기간 정책·로그인·브라우저 영속 저장 추가 금지. 혼합 행은 이동 없이 토글.
- layout: 56px 헤더, 240/64px sidebar, 280px mobile overlay, 독립 스크롤, 얇은 divider, 청색 active, 4px controls, 20px 제목. 즐겨찾기·최근 본 화면·화면 탭·상단 도구줄·단축키·알림은 비활성.

## 화면별 확인표

아래 모든 행의 CTA/field/payload/validation/success는 위 공통 계약을 적용한다. 이름과 메뉴 경로는 초기값 설명일 뿐 구현 상수가 아니며 실제 DB가 우선한다.

| route | 사용자 목적 / 초기 메뉴 경로 | 추가 제약 |
| --- | --- | --- |
| /screens/UI-16 | 대시보드/대시보드 자리 확인 | KPI 생성 금지; /는 첫 가시 leaf 자동 선택 |
| /screens/UI-15 | 기준정보/거래처·영업 파트 자리 확인 | 업무 입력 없음 |
| /screens/UI-02 | 기준정보/개발자 풀 자리 확인 | 업무 입력 없음 |
| /screens/UI-01 | 프로젝트·투입/프로젝트 자리 확인 | 프로젝트 CRUD 생성 금지 |
| /screens/UI-31 | 프로젝트·투입/프로젝트 현황판 자리 확인 | 업무 입력 없음 |
| /screens/UI-12 | 프로젝트·투입/투입 배정 자리 확인 | 업무 입력 없음 |
| /screens/UI-05 | 프로젝트·투입/철수 리스트 자리 확인 | 업무 입력 없음 |
| /screens/UI-11 | 프로젝트·투입/인원 계획 대비 투입 자리 확인 | 업무 입력 없음 |
| /screens/UI-28 | 인력 계획/인력 운영 보드 자리 확인 | 업무 입력 없음 |
| /screens/UI-17 | 인력 계획/인력 수요 예측 자리 확인 | 업무 입력 없음 |
| /screens/UI-29 | 인력 계획/영업 파이프라인 자리 확인 | 업무 입력 없음 |
| /screens/UI-33 | 인력 계획/외부 인력 후보 자리 확인 | 업무 입력 없음 |
| /screens/UI-13 | 매출/청구·입금 자리 확인 | 업무 입력 없음 |
| /screens/UI-03 | 원가·수익/개인 원가·수익표 자리 확인 | 업무 입력 없음 |
| /screens/UI-04 | 원가·수익/프로젝트 원가 분석 자리 확인 | 업무 입력 없음 |
| /screens/UI-19 | 원가·수익/영업 파트 이익금 자리 확인 | 업무 입력 없음 |
| /screens/UI-32 | 원가·수익/단가 재협상 대상 자리 확인 | 업무 입력 없음 |
| /screens/UI-10 | 재무·자금/재무 항목 자리 확인 | PC전용; 폭 왕복 시 같은 주소 복구 |
| /screens/UI-07 | 재무·자금/전체 수익 구조 자리 확인 | 업무 입력 없음 |
| /screens/UI-08 | 재무·자금/영업이익표 자리 확인 | 업무 입력 없음 |
| /screens/UI-09 | 재무·자금/순이익표 자리 확인 | 업무 입력 없음 |
| /screens/UI-18 | 재무·자금/사업계획 대비 실적 자리 확인 | 업무 입력 없음 |
| /screens/UI-21 | 재무·자금/자금수지표 자리 확인 | 업무 입력 없음 |
| /screens/UI-14 | 마감/월 마감 자리 확인 | PC전용; 모바일 최종 빈 마감그룹 숨김 |
| /screens/UI-26 | AI/AI 분석 도우미 자리 확인 | AI 호출 없음 |
| /screens/UI-30 | AI/AI 인력 추천 자리 확인 | AI 호출 없음 |
| /screens/UI-23 | 시스템/사용자 자리 확인 | PC전용; LOCK_YN 필터 없음 |
| /screens/UI-24 | 시스템/권한 자리 확인 | PC전용; 권한 명령 없음 |
| /screens/UI-25 | 시스템/메뉴 자리 확인 | PC전용; 메뉴 편집 없음 |
| /screens/UI-27 | 시스템/공통코드 자리 확인 | PC전용; 코드 편집 없음 |

## 검증 경계

기존 OpenAPI는 screenAccess 메타의 세부 스키마를 명시하지 않지만 실제 backend DTO/service가 제공하므로 기존 구현 계약을 유지한다. OQ-002/OQ-005의 명세 승인 여부를 임의로 변경하지 않는다. 역할별 테스트 대신 명세의 유일 사용자 범위(무인증 개발 PC)와 모바일 표시 제약을 렌더링 테스트로 검증한다. 실제 DB 통합은 Docker daemon 가용성을 별도로 확인해야 한다.

## 수정 후 대조 및 실행 결과

- 화면 목록: 기존 동적 /screens/:scrnId가 모든 30개 화면과 DB 추가 화면을 지원한다. 누락 route 없음. 각 화면 선택·직접 진입·재생성은 기존 렌더링 테스트로 검증했다.
- 기능 범위: 입력폼·업무 조회/저장/삭제·확인 모달을 만들지 않았다. DB 메뉴명·경로·아이콘 및 실제 screenAccess 제한을 유지했다. 여섯 후속 도구는 계속 disabled다.
- 시각 개선: 레퍼런스의 브랜드 영역, 본문 breadcrumb/H1/screen ID, 그룹 chevron/active ancestor, 36px/40px/36px 컨트롤 밀도와 청색 active, 비활성 도구 아이콘, 상태별 안내 및 정적 skeleton을 적용했다. 본문은 업무 카드가 아닌 상태 안내 surface다.
- UX 수정: 64px 접힘 메뉴의 flyout overflow 및 hover/focus 노출, 모바일에서 현재 route를 포함한 메뉴 선택 후 닫힘/토글로 포커스 복귀, route 변경 시 본문 스크롤 초기화, 본문 바로가기.
- Node 20.19.5에서 Vitest 2.1.9 + jsdom: 4 files / 73 tests passed. 소스 문자열 비교 시험을 추가하지 않았다. 새 회귀 시험 두 개는 수정 전 실패하고 수정 후 통과했다.
- Node 20.19.5에서 TypeScript --noEmit 통과, Vite 5.4.14 production build 통과.
- Playwright 1.51.1의 Chromium 브라우저 점검: 실제 참조 사이트 그룹 펼침 및 #UI-01 이동 확인. 앱 테스트용 API 응답에서는 56px header, 240/64px sidebar, flyout hit-test, 390px 모바일 PC전용/route 이동/닫힘, 가로 overflow 없음, dark 색상 및 런타임 오류 없음 확인. 이 점검은 실 DB 통합 결과가 아니다.
- 실제 backend 없는 preview에서는 양쪽 오류 및 다시 시도가 표시됨을 확인했다. Docker daemon 연결 실패, Java/Maven/PostgreSQL 실행 도구 부재로 실 DB 연동 재실행은 차단됐다. backend/API/persistence 파일은 수정하지 않았다.
- git diff --check 및 이번 변경 파일의 trailing whitespace 검사 통과. 기존 사용자 변경은 보존했다.

## UI Contract 느슨한 재점검

- 점검 범위: ASCII 좌표·간격은 수정 기준에서 제외하고, 공통 30개 route의 메뉴 탐색·직접 진입·재시도·제한 우선순위·폭 변경·비활성 도구를 기존 구현 및 렌더링 시험과 대조했다.
- 명백한 drift 보강: A09/AC-UI-018에서 미등록 주소의 가시 메뉴가 0개이면 복귀 CTA가 사라졌다. 이제 첫 화면 링크를 유지하며, 목적지가 없으면 /로 이동하여 빈 안내를 표시한다. 클릭 전에는 원주소와 미등록 안내를 유지한다.
- 회귀 시험: 정상 빈 응답 및 휴대폰에서 PC전용 메뉴만 남은 조건을 각각 렌더링한다. 복귀 CTA 클릭, / 이동, 본문 빈 안내, 미등록 안내 해제, 입력폼·오류 재시도 부재, 추가 API 요청 없음을 확인한다. 수정 전 두 조건 모두 CTA 부재로 실패했으며 수정 후 통과했다.
- 실행 결과: npm test -- --run --maxWorkers=2 --minWorkers=1에서 4 files / 74 tests passed. npm run build의 TypeScript 검사 및 Vite production build 통과. 시험은 Vitest + jsdom의 컴포넌트 렌더링이며 소스 읽기·raw import 비교를 사용하지 않는다.
- 적용 제외: 명세는 무인증 개발 PC 사용자만 정의한다. 역할·기간 제한, create/edit, readonly 입력, 쓰기 payload, field-level ApiError.fields를 소비하는 폼은 존재하지 않아 추가하지 않았다. PC/휴대폰의 표시 제약은 기존 렌더링 시험으로 확인한다.
- 남은 계약 gap: OpenAPI meta는 추가 필드를 허용하지만 screenAccess의 세부 스키마는 없다. 현재 backend MenuResponse.ScreenAccess 및 MenuRules가 제공하는 기존 확장을 유지한다. OQ-002의 원문 배열/봉투 충돌, OQ-005의 판별 정보·혼합 화면 직접 진입 승인 여부는 frontend가 확정하지 않는다.
- 미정 표시 정책: OQ-UI-001 로딩 문구와 OQ-003 휴대폰 경계는 기존 구현을 유지했다. 이번 점검은 API 응답 대역을 이용한 frontend 검증이며 실제 DB 통합·브라우저 시각 검증을 새로 수행한 결과가 아니다. backend/API/persistence 및 명세는 변경하지 않았다.
