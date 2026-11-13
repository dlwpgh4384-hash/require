export interface OperatingStandard {
  standardCode: string;
  standardName: string;
  value: string;
  unit: string;
  effectiveMonth: string | null;
  closed: boolean;
  immediate: boolean;
}
export interface OperatingStandardChange {
  standardCode: string;
  value?: string;
  effectiveMonth: string | null;
  action: "add" | "update" | "delete";
}
export interface OperatingStandardsResult {
  items: OperatingStandard[];
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
      response.status === 400 && typeof body.message === "string"
        ? body.message
        : "운영 기준값을 처리하지 못했습니다. 다시 시도해 주세요.",
      response.status === 400 && Array.isArray(body.errors) ? body.errors : [],
    );
  if (!Array.isArray(body.data?.items))
    throw new Error("운영 기준값 응답을 확인해 주세요.");
  return body.data;
}
export const getCommonOperatingStandards = (signal?: AbortSignal) =>
  operatingRequest({ signal });
export const putCommonOperatingStandards = (items: OperatingStandardChange[]) =>
  operatingRequest({
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items }),
  });

export const operatingGroups = [
  {
    title: "파트·세금·목표",
    codes: [
      "PART_DSTRB_RT",
      "PART_SALES_EXPS",
      "VAT_RT",
      "TGT_UTIL_RT",
      "TGT_MRGN_RT",
      "LOW_MRGN_GAP",
    ],
  },
  { title: "미수 알림", codes: ["DUN_D1", "DUN_D2", "DUN_D3"] },
  { title: "철수 알림", codes: ["WTHDR_NOTI_D"] },
  {
    title: "연장 확률",
    codes: ["EXT_PRBL_H", "EXT_PRBL_M", "EXT_PRBL_L", "EXT_PRBL_NULL"],
  },
  {
    title: "영업 단계 확률",
    codes: ["STEP_PRBL_1", "STEP_PRBL_2", "STEP_PRBL_3", "STEP_PRBL_4"],
  },
  { title: "청구 누락 오차", codes: ["BILL_MISS_RT", "BILL_MISS_MIN"] },
  { title: "최소 운영자금", codes: ["MIN_CASH"] },
  {
    title: "AI 배점",
    codes: [
      "AI_SCORE_JOB",
      "AI_SCORE_SKILL",
      "AI_SCORE_CUST",
      "AI_SCORE_GRADE",
      "AI_SCORE_PERF",
      "AI_SCORE_COST",
      "AI_SCORE_AVAIL",
      "AI_SCORE_LOC",
    ],
  },
  { title: "자금수지", codes: ["CASH_BASE_YM", "CASH_BASE_AMT"] },
];
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
  const ai = operatingGroups.find((group) => group.title === "AI 배점")!.codes;
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
