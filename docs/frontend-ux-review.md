# Frontend UI 계약 대조 및 검증

## 범위와 근거

- `.aiops-spec/ui-design.md`의 화면은 SCR-COMMON 한 개다. UI-xx 메뉴 대상은 같은 shell의 `준비 중` 안내이며 업무 CRUD 화면이 아니다.
- 기존 진입 `/`, 대상 `/screens/:scrnId`, 잘못된 주소의 shell fallback을 유지했다. 스타일 가이드의 예제 업무 route·로그인·테마 전환은 제품 기능으로 추가하지 않았다.
- `.aiops-spec/code-context/implementation-map.json`은 이번 입력에 존재하지 않았다. 읽기 전용 명세 사본은 수정하지 않았다.
- 현재 backend 구현과 기존 confirmations의 추천 선택을 따른다. 명세 수용이 개별 OQ의 사람 승인을 의미하지는 않는다. 관련 선택은 이번 call_id의 outbox에 `provisional_pending`, `origin=code`로 기록했다.

## 코딩 전 계약 체크리스트와 구현 대조

| 항목 | 필수 계약 | 구현 및 검증 |
|---|---|---|
| route·사용자 목적 | DB 메뉴를 탐색하고 대상 안내 확인 | 기존 React Router Link/useNavigate 보존. 직접 진입·최초 선택·미등록 복귀 렌더링 시험 |
| archetype·영역 | 단일 Header/Sidebar/본문의 tree navigation | 기존 컴포넌트 소유권 유지. 같은 shell 안에서 상태 안내 교체 |
| CTA | 일반/혼합 그룹 토글, leaf 이동, 오류 재시도, 첫 화면 복귀 | 실제 route와 재조회 연결 유지. 현재 대상의 조상 자동 펼침, 키보드 조작, 중첩 그룹 접기 후 펼침 상태 유지 |
| 필드 동작 | 공개 여덟 필드 모두 readonly | menuNm/iconNm/계층/순서/scrnId/폭 필터를 실제 응답으로 표시. 입력 폼·편집 기능 없음 |
| payload | 쓰기 API·저장·삭제 없음 | GET /api/common/menus만 호출. backend·DB 변경 없음 |
| validation | 응답 계약과 URL 오류 구분 | 이중 봉투·각 메뉴 필드·화면 메타 형식 확인. 잘못된 응답은 재시도 가능한 오류로 표시. 상속된 Object 속성을 화면 상태로 오판하지 않음 |
| success | DB 순서상 첫 화면, 선택 대상 준비 중, 재조회 복구 | 기존 첫 화면 계산 유지. 복구 시 요청 전 URL 대상 유지. 중복 scrnId는 backend가 고른 menuId 한 행만 활성 표시 |
| state·제약 | loading/error/empty/비활성/PC 전용/미등록 구분 | 공통 StateNotice의 제목·설명·아이콘, 로딩 skeleton/진행 표시, 오류 강조. 폭 복원 시 같은 대상 복원 |
| permission·역할 | 무인증, 역할 필터 없음, 표시 제한은 서버 인가 아님 | 로그인·역할 입력·403 화면 신설 없음. 사용 불가·PC 전용 표시만 수행 |
| 후속 자리 | 여섯 기능 비활성 | 즐겨찾기/최근 화면/화면 탭/도구줄/단축키/알림에 실행 handler 없음 |
| 상태 저장 | 메모리만 사용 | DB·localStorage·sessionStorage 저장 추가 없음 |

## 실제 API 연결 추적

1. `frontend/src/App.tsx` → `loadMenus(AbortSignal)` → `GET /api/common/menus`.
2. `MenuController.listCommonMenus` → `MenuService.list` → `MenuMapper.findAll` → PostgreSQL `TB_SY_MENU`.
3. Mapper는 quoted camelCase alias로 필드를 읽는다. `MenuGraph`가 활성·계층 판정과 화면 메타데이터를 계산한다.
4. frontend는 `data.data`의 메뉴 배열, `meta.parentMenuIds`의 원본 그룹 의미, `meta.screenStates`의 주소 판정을 사용한다.
5. Sidebar는 실제 배열의 계층·sortOrd/menuId 순서를 유지하고 휴대폰에서 PC 전용 leaf와 빈 그룹을 제외한다. 쓰기 API는 없다.

## 스타일 적용

설치된 UI 시스템이 React/기존 CSS/SVG뿐이므로 새 UI 라이브러리·패키지는 추가하지 않았다. default-ui-style.md의 Civic Blue OKLCH 의미 토큰, 0.625rem radius 체계, 64px 상단, 밝은 sidebar/표면 계층, 제한된 제목 밴드, 카드형 상태 안내, 일관된 SVG 크기, focus-visible ring, hover/chevron transition, reduced-motion 대응을 적용했다. 기존 모바일 breakpoint와 흐름형 메뉴를 유지했다. 업무 지표·가짜 사용자·건수·예시 메뉴·장식 이미지는 추가하지 않았다.

## 실제 실행 결과

최종 검증 도구: Node v20.18.0, npm 10.8.2. 기본 호스트 Node 22 대신 프로젝트 engines의 Node 20으로 재검증했다.

frontend/에서 실행:

- `npm ci --no-audit --no-fund`: 성공. package-lock.json으로 재현 가능 설치 확인.
- `npm run build`: 성공. TypeScript 검사와 Vite production bundle 생성 완료.
- `npm test -- --run --maxWorkers=2 --minWorkers=1`: Vitest + jsdom 22개 통과.
- `UI_BASE_URL=http://127.0.0.1:4173 npm run test:e2e -- tests/e2e/shell-states.spec.ts --workers=2`: Chromium 4개 통과.

컴포넌트 시험은 App을 실제 렌더링하고 역할 기반 메뉴·버튼·링크 및 키보드 입력 결과를 단언한다. 소스 파일 읽기/문자열 비교/raw import 시험은 사용하지 않았다. API 응답 fixture는 컴포넌트 시험 안에서만 사용한다.

브라우저 시험은 API 연결 실패를 명시적으로 주입한 frontend-only 시험이다. 320/390/768/1280px에서 `/`, `/screens/UI-01`, 미등록 대상 주소를 실제 preview로 열어 HTTP 200 shell, 오류 안내, 재시도 재요청, 여섯 비활성 자리, shell 단일성, 가로 overflow 없음, pageerror 없음이 통과했다. 스크린샷은 `frontend/test-results/`의 각 시험 출력에 저장된다. 이는 DB 정상 메뉴·성공 화면의 시각 검증이나 실제 backend 통합 통과를 의미하지 않는다.

## 남은 검증 경계

- Docker daemon 연결 실패: `Cannot connect to the Docker daemon at unix:///var/run/docker.sock`.
- 따라서 기존 `tests/e2e/menu-navigation.spec.ts`의 실제 PostgreSQL/backend 메뉴 통합 시험은 이번에 실행하지 못했다. backend 및 persistence는 수정하지 않았다.
- Chromium 기본 installer는 64MiB scratch 공간 부족으로 실패했다. 같은 공식 다운로드를 메모리에서 풀어 브라우저 캐시에 설치한 뒤 위 Chromium 시험을 실제 실행했다.
- React Router v7 future flag 안내와 whatwg-encoding 의존성 deprecation 경고는 남아 있다. 시험 실패는 아니다.
- URL·breakpoint·응답 봉투·주소 메타·경계 상태의 기존 잠정 선택은 사람 승인으로 승격하지 않았다. outbox에는 선택 질문만 기록하고 작업 보고는 넣지 않았다.
