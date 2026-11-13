export interface OperatingStandard {
  standardCode: string;
  standardName: string;
  value: string;
  unit: string;
  effectiveMonth: string | null;
  closed: boolean;
  immediate: boolean;
  /** 이력 창 「처리자」. 처음 값은 「처음 설정」. */
  changedBy?: string | null;
  /** 이 기준값 코드의 마지막 변경 시각(UTC). 저장할 때 돌려보낸다(FR-0106 동시 수정). */
  modifiedAt?: string | null;
}
export interface OperatingStandardChange {
  standardCode: string;
  value?: string;
  effectiveMonth: string | null;
  action: "add" | "update" | "delete";
  modifiedAt?: string | null;
}
export interface OperatingStandardsResult {
  items: OperatingStandard[];
  /** 마지막으로 마감한 달(YYYY-MM). 저장 창 문구에 쓴다. */
  closedMonth?: string | null;
}
export class OperatingStandardsRequestError extends Error {
  constructor(
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
async function operatingRequest(
  init?: RequestInit,
): Promise<OperatingStandardsResult> {
  const response = await fetch("/api/common/operating-standards", init);
  const body = await response.json();
  if (!response.ok || body.success !== true)
    throw new OperatingStandardsRequestError(
      (response.status === 400 || response.status === 409) &&
        typeof body.message === "string"
        ? body.message
        : "운영 기준값을 처리하지 못했습니다. 다시 시도해 주세요.",
      response.status === 400 && Array.isArray(body.errors) ? body.errors : [],
    );
  if (!Array.isArray(body.data?.items))
    throw new Error("운영 기준값 응답을 확인해 주세요.");
  return {
    ...body.data,
    items: body.data.items.map((row: OperatingStandard) => ({
      ...row,
      value: trimOperatingValue(row.value),
    })),
  };
}
/** 화면에는 끝자리 0을 빼고 보인다(시안: 40.0000 → 40, 0.0330 → 0.033). */
export const trimOperatingValue = (value: string) =>
  /^-?[0-9]+\.[0-9]+$/.test(value)
    ? value.replace(/0+$/, "").replace(/\.$/, "")
    : value;
export const getCommonOperatingStandards = (signal?: AbortSignal) =>
  operatingRequest({ signal });
export const putCommonOperatingStandards = (items: OperatingStandardChange[]) =>
  operatingRequest({
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items }),
  });

/**
 * 시안 UI-27 [운영 기준값] 표의 줄(시안 순서·이름·쓰는 곳 그대로). 칸이 여러 개인 줄(parts)은
 * 한 줄에 입력칸 여러 개를 두고 칸마다 코드 한 줄로 저장한다(SFR-127-08).
 */
export interface OperatingRowDef {
  key: string;
  label: string;
  codes: string[];
  parts?: string[];
  uses: string;
}
export const operatingRows: OperatingRowDef[] = [
  { key: "PART_DSTRB_RT", label: "파트 이익 배분율(새 파트 기본)", codes: ["PART_DSTRB_RT"], uses: "식-43" },
  { key: "PART_SALES_EXPS", label: "공통관리비(1인·월, 새 파트 기본)", codes: ["PART_SALES_EXPS"], uses: "식-42" },
  { key: "VAT_RT", label: "부가세율", codes: ["VAT_RT"], uses: "식-05·49" },
  { key: "WTHLD_RT", label: "원천세율", codes: ["WTHLD_RT"], uses: "식-17·49" },
  { key: "TGT_UTIL_RT", label: "목표 가동률", codes: ["TGT_UTIL_RT"], uses: "식-37·65" },
  { key: "TGT_MRGN_RT", label: "목표 마진율", codes: ["TGT_MRGN_RT"], uses: "식-19·21·56·85·90" },
  { key: "QUOTE_APRV_RT", label: "견적 승인 기준 이익률", codes: ["QUOTE_APRV_RT"], uses: "식-86·87" },
  { key: "QUOTE_REAPRV_GAP", label: "재승인 이익률 차이", codes: ["QUOTE_REAPRV_GAP"], uses: "식-86" },
  { key: "LOW_MRGN_GAP", label: "저마진 경고폭", codes: ["LOW_MRGN_GAP"], uses: "식-94" },
  { key: "BDGT_WARN_RT", label: "예산 소진 주의", codes: ["BDGT_WARN_RT"], uses: "식-19" },
  { key: "DUN_D", label: "미수 알림 1·2·3단계", codes: ["DUN_D1", "DUN_D2", "DUN_D3"], parts: ["1단계", "2단계", "3단계"], uses: "식-89" },
  { key: "WTHDR_NOTI_D", label: "철수 알림", codes: ["WTHDR_NOTI_D"], uses: "공통-15" },
  { key: "EXT_PRBL", label: "연장 확률 높음·보통·낮음·미정", codes: ["EXT_PRBL_H", "EXT_PRBL_M", "EXT_PRBL_L", "EXT_PRBL_NULL"], parts: ["높음", "보통", "낮음", "미정"], uses: "식-60" },
  { key: "STEP_PRBL", label: "영업 단계 확률", codes: ["STEP_PRBL_1", "STEP_PRBL_2", "STEP_PRBL_3", "STEP_PRBL_4"], parts: ["발굴", "제안", "협상", "계약 직전"], uses: "식-61" },
  { key: "SHORT_PERSON", label: "인력 부족 기준", codes: ["SHORT_PERSON"], uses: "식-62·93" },
  { key: "SURPLUS_PERSON", label: "인력 남음 기준", codes: ["SURPLUS_PERSON"], uses: "식-62" },
  { key: "HIRE_LEAD_MON", label: "채용 준비 기간", codes: ["HIRE_LEAD_MON"], uses: "식-93" },
  { key: "MIN_CASH", label: "최소 운영자금", codes: ["MIN_CASH"], uses: "식-51" },
  { key: "CASH_BASE_YM", label: "자금수지 시작 월", codes: ["CASH_BASE_YM"], uses: "식-46" },
  { key: "CASH_BASE_AMT", label: "자금수지 기초 잔액", codes: ["CASH_BASE_AMT"], uses: "식-46" },
  { key: "BILL_MISS", label: "청구 누락 허용 오차", codes: ["BILL_MISS_RT", "BILL_MISS_MIN"], parts: ["비율", "최소 금액"], uses: "식-88" },
  { key: "COST_OVER_UPRC_RT", label: "단가 대비 원가 기준", codes: ["COST_OVER_UPRC_RT"], uses: "식-91" },
  { key: "MM_OVER", label: "MM 초과 경고 기준", codes: ["MM_OVER"], uses: "식-95" },
  { key: "LOCK_FAIL_CNT", label: "로그인 잠금 실패 횟수", codes: ["LOCK_FAIL_CNT"], uses: "공통-07" },
  { key: "SESSION_MIN", label: "자동 로그아웃", codes: ["SESSION_MIN"], uses: "공통-07" },
  { key: "PWD_CHG_DAY", label: "비밀번호 변경 안내 주기", codes: ["PWD_CHG_DAY"], uses: "공통-07" },
  {
    key: "AI_SCORE",
    label: "AI 추천 배점(합계 100)",
    codes: ["AI_SCORE_JOB", "AI_SCORE_SKILL", "AI_SCORE_CUST", "AI_SCORE_GRADE", "AI_SCORE_PERF", "AI_SCORE_COST", "AI_SCORE_AVAIL", "AI_SCORE_LOC"],
    parts: ["직무", "스킬", "고객/업종", "등급", "성과", "비용", "가용일", "근무지"],
    uses: "식-68",
  },
];
const AI_CODES = operatingRows.find((row) => row.key === "AI_SCORE")!.codes;
/** 예전 묶음 이름과의 호환(계산·검사에서 씀). */
export const operatingGroups = operatingRows.map((row) => ({
  title: row.key === "AI_SCORE" ? "AI 배점" : row.label,
  codes: row.codes,
}));
export const operatingKey = (row: OperatingStandard) =>
  `${row.standardCode}-${row.effectiveMonth ?? "immediate"}`;
export const operatingTestKey = (code: string) =>
  code.toLowerCase().replaceAll("_", "-");
export function currentOperatingRows(
  items: OperatingStandard[],
  month: string,
) {
  return operatingGroups.flatMap((group) =>
    group.codes.flatMap((code) => {
      const history = items
        .filter((row) => row.standardCode === code)
        .sort((a, b) =>
          (a.effectiveMonth ?? "").localeCompare(b.effectiveMonth ?? ""),
        );
      const active = history.filter(
        (row) => row.immediate || row.effectiveMonth! <= month,
      );
      return active.length ? [active[active.length - 1]] : history.slice(0, 1);
    }),
  );
}
export function validateOperatingChanges(
  items: OperatingStandard[],
  changes: OperatingStandardChange[],
  month: string,
) {
  const errors: Record<string, string> = {};
  for (const change of changes) {
    if (change.action === "delete") continue;
    if (change.standardCode === "CASH_BASE_YM") {
      if (!/^[0-9]{4}-(0[1-9]|1[0-2])$/.test(change.value ?? ""))
        errors[change.standardCode] = "시작 월을 확인해 주세요.";
      continue;
    }
    if (!/^-?[0-9]+(?:\.[0-9]{1,4})?$/.test(change.value ?? ""))
      errors[change.standardCode] = "소수 네 자리 이내 숫자를 입력해 주세요.";
    if (
      (change.standardCode.startsWith("EXT_PRBL_") ||
        change.standardCode.startsWith("STEP_PRBL_")) &&
      (Number(change.value) < 0 || Number(change.value) > 100)
    )
      errors[change.standardCode] = "확률은 0~100으로 입력해 주세요.";
  }
  const effective = new Map(
    currentOperatingRows(items, month).map((row) => [
      row.standardCode,
      row.value,
    ]),
  );
  changes
    .filter((row) => row.action !== "delete")
    .forEach((row) => effective.set(row.standardCode, row.value!));
  const ai = AI_CODES;
  if (
    changes.some((row) => ai.includes(row.standardCode)) &&
    ai.reduce((sum, code) => sum + Number(effective.get(code)), 0) !== 100
  )
    changes
      .filter((row) => ai.includes(row.standardCode))
      .forEach((row) => {
        errors[row.standardCode] = "AI 배점 합계는 100이어야 합니다.";
      });
  if (changes.some((row) => row.standardCode.startsWith("DUN_D"))) {
    const values = ["DUN_D1", "DUN_D2", "DUN_D3"].map((code) =>
      Number(effective.get(code)),
    );
    if (!(values[0] < values[1] && values[1] < values[2]))
      changes
        .filter((row) => row.standardCode.startsWith("DUN_D"))
        .forEach((row) => {
          errors[row.standardCode] =
            "미수 알림은 1단계 < 2단계 < 3단계로 입력해 주세요.";
        });
  }
  return errors;
}
