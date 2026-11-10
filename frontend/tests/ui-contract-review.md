# UI Contract 경량 점검

## 범위와 결론

.aiops-spec/ui-design.md의 SCR-COMMON 사용자 기능 계약을 현재 frontend와 대조했다. ASCII 위치·간격·컬럼 순서는 합격 기준으로 삼지 않았다. 명백하게 빠진 제품 동작은 확인되지 않아 frontend/src와 backend를 변경하지 않았다. 기존 기능을 불필요하게 재작성하는 대신 누락되기 쉬운 수용 조건의 렌더링 시험을 보강했다.

## 화면 체크리스트

| 항목 | 현재 매핑과 점검 결과 |
| --- | --- |
| route / 목적 / archetype | App의 / 및 /screens/:scrnId, 기타 주소도 단일 shell 안에서 처리. 실제 API 계층 탐색과 안내 본문이며 별도 업무 화면이 아니다. route는 기존 잠정 선택을 유지한다. |
| menu path / layout | DB upperMenuId 계층과 sortOrd·menuId 순서, Header·Sidebar·본문 하나씩. 가짜 중메뉴를 추가하지 않는다. |
| CTA | 일반·혼합 그룹은 토글만, leaf는 화면 선택, 오류는 재요청, 미등록만 사용자가 첫 화면으로 복귀한다. |
| readonly / create-edit / payload | 서버 값은 표시 전용. create/edit, 변경 payload, 삭제 확인, 기간 입력은 계약상 해당 없음. 새 폼이나 쓰기 요청을 만들지 않는다. |
| validation | 요청 실패·잘못된 응답은 복구 가능한 오류. 폼이 없어 field-level ApiError.fields 표시는 해당 없음. |
| success / selection | 최초 선택과 복귀는 API 순서 기반. 직접 진입 및 재시도 뒤 선택 유지, PC 폭 복원 시 같은 대상 복원. |
| permission / role | localhost 비로그인만 정의됨. 역할별 메뉴 필터나 로그인·인가·잠금 기능을 발명하지 않는다. PC 전용, 사용 불가, 미등록을 구별한다. |
| states | loading·error·empty·준비 중·사용 불가·PC 전용·미등록. 조회 중/실패를 준비 중 성공으로 표시하지 않는다. 여섯 후속 기능 자리는 비활성이다. |
| persistence | 탐색·폭 변경은 추가 API 요청이나 브라우저 저장 없이 로컬 동작한다. |

## 추가한 렌더링 시험

frontend/tests/layout.test.tsx에 다섯 사례를 추가했다.

- API sortOrd 변경을 반영한 최초 선택과 명시적 첫 화면 복귀.
- 새 방문에서 이전 선택을 복원하지 않고 API 순서와 menuId 동률 순서를 다시 적용.
- 비로그인 모바일 메뉴 펼침·선택·접기·재펼침과 선택 강조, 추가 요청·저장 없음.
- 모바일 PC 전용 주소 직접 진입 뒤 PC 폭에서 같은 대상 복원, 추가 요청 없음.
- 비활성·PC 전용 중첩에서 현재 잠정 표시 우선순위와 CTA 부재.

기존 시험과 함께 Vitest + jsdom에서 App을 렌더링하고 role·화면 문구·사용자 입력 결과를 단언한다. 소스 파일 읽기·raw import·소스 문자열 비교 시험은 사용하지 않는다. 시험 응답은 제품 더미 메뉴가 아닌 기존 단위시험의 fetch 경계 fixture를 사용한다.

## 남은 gap / 사람 확인

- .aiops-spec/code-context/implementation-map.json은 제공된 작업 트리에 없었다. 해당 디렉터리의 map 이름 검색에서도 발견되지 않았다. 지도 버전·SHA·task ID를 임의 생성하지 않았다.
- OQ-003: 원문 배열과 실제 OpenAPI·backend 이중 data 봉투 차이는 남는다. 현재 봉투를 유지한다.
- OQ-005 / OQ-ARCH-001 / OQ-UI-006: 비활성·미등록과 필터 전 혼합 그룹 구별은 현재 backend의 meta.screenStates·meta.parentMenuIds에 의존한다. 필터 결과 부재만으로 상태를 꾸며내지 않는다. 현재 통로의 제품 선택은 사람 확인 대기다.
- OQ-004 / OQ-UI-002: route·768px 기준은 기존 잠정 선택을 유지하며 정식 승인 완료로 보지 않는다.
- OQ-010 / OQ-011: 중첩 제한 우선순위·첫 화면 부재·loading·재시도 정책은 기존 추천 선택을 유지한다. 시험 통과가 정책의 사람 승인을 뜻하지 않는다.
- 실제 DB 이름·순서 변경과 서버 복구를 포함한 브라우저 E2E, 실제 CSS 배치와 레퍼런스의 시각 대조는 이번 jsdom 점검에서 재실행하지 않았다. 단위시험의 fetch fixture 통과를 실제 DB 수용 증거로 간주하지 않는다.

사람이 정할 기존 네 선택만 .aiops-code-context/outbox/d064b5119a4340f6bafa37191bdc31dc.json에 origin=code, provisional_pending 및 기존 source_decision_id로 기록했다. 명세 사본은 수정하지 않았다.

## 실행 결과

frontend에서 실행:

- npm test -- --run: 27 tests passed.
- npm run build: tsc --noEmit 및 Vite production build 통과.

React Router의 v7 future flag 및 환경의 UNDICI-EHPA 경고는 있었으나 시험·빌드 실패는 없었다. 이 결과는 경량 점검이며 전체 명세 수용이나 미정 사항 해제를 선언하지 않는다.
