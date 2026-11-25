import {
  developerRequest,
  DeveloperRequestError,
} from "../developers/developers";
import { addDays } from "../../shared/time";
export { DeveloperRequestError as SalesOpportunityError };
export interface SalesNeed {
  needSn?: number;
  jobCode: string;
  gradeCode: string | null;
  requiredCount: number;
  startMonth: string;
  months: number;
  monthlyMm: number;
  estimatedUnitPrice: number | null;
  tentativeDeveloperIds: string[];
}
export interface SalesOpportunityInput {
  opportunityName: string;
  customerId: string;
  salesPartId: string;
  stageCode: string;
  winningProbability: number;
  expectedAmount: number;
  contractTypeCode: string | null;
  startMonth: string;
  expectedMonths: number | null;
  workplace: string | null;
  /** 근무지 지역(선택, 2026-11-25): 고른 마지막 단계의 행정구역 코드. 수주 처리하면 프로젝트로 넘어간다. */
  workAreaCode?: string | null;
  nextAction: string | null;
  nextActionDueDate: string | null;
  memo: string | null;
  needs: SalesNeed[];
}
export interface SalesOpportunity extends SalesOpportunityInput {
  opportunityId: string;
  customerName: string;
  /** 근무지 지역 보여 주기 글자(시·도 줄임 이름 + 시·군·구 + 동). 없으면 null. */
  workAreaName?: string | null;
  modifiedAt: string;
  approvalStatusCode: string;
  opportunityStatusCode: string;
  projectId: string | null;
  resultDate: string | null;
  lossReasonCode: string | null;
  lossMemo?: string | null;
  /** 2주(14일) 넘게 갱신 없음(서버 계산). */
  stale?: boolean;
  /** 저장 때 판정한 예상 이익률(TB_SL_OPP.EXPCT_PRFT_RT). 승인 대기 띠에 쓴다. */
  expectedProfitRate?: number | null;
  /** 승인한 때의 예상 이익률(TB_SL_OPP.APRV_PRFT_RT). 승인됨 띠에 쓴다. */
  approvedProfitRate?: number | null;
  /** 이번 달 견적 승인 기준 이익률(띠의 「(기준 n%)」). */
  approvalRate?: number | null;
  approvalHistory: {
    actionCode: string;
    actedAt: string;
    reason: string | null;
  }[];
}
export interface SalesOpportunityOptions {
  codes: Record<
    string,
    { code: string; codeName: string; estimateUnitPrice?: number }[]
  >;
  customers: { clientId: string; clientName: string }[];
  parts: { partId: string; partName: string }[];
  developers: {
    developerId: string;
    name: string;
    primaryJobCode?: string;
    primaryJobName?: string;
    workStatusName?: string;
    withdrawalDate?: string | null;
  }[];
}
export interface SalesQuote {
  expectedRevenue: number;
  expectedCost: number;
  profitRate: number;
  minimumUnitPrice: number;
  targetUnitPrice: number;
  breakEvenAmount?: number;
  targetAmount?: number;
  approvalRate?: number;
  /** 목표 이익률(운영 기준값 TGT_MRGN_RT). 서버가 주면 「목표 이익률 n%」로 보인다. */
  targetRate?: number;
  reapprovalGap?: number;
  profitBand?: string;
  needQuotes?: {
    rowIndex: number;
    minimumUnitPrice: number;
    targetUnitPrice: number;
    /** 계산에 쓴 단가(비우면 직무 기준 단가). */
    unitPrice?: number;
  }[];
}
/** 화면 알림(공통 Toast): 성공 ok, 실패·409 warn, 안내 info. */
export interface SalesNotice {
  message: string;
  kind: "ok" | "warn" | "info";
}
/** 기회·수주 처리에서 고르는 계약유형: 턴키·월단위·파견(SFR-129-03·09). 내부(자체 개발)는 뺀다. */
export const SALES_CONTRACT_TYPES = ["TK", "MO", "DP"];
const root = "/api/common/sales-opportunities";
export const getCommonSalesOpportunity = (id: string) =>
  developerRequest<SalesOpportunity>(`${root}/${encodeURIComponent(id)}`);
/**
 * 저장·처리(PUT·POST·수주·실주·삭제) 응답이 최신 기회다. 응답에 기회가 없을 때만 다시 읽고,
 * 그 읽기가 실패해도 쓰기는 이미 끝났으므로 실패로 알리지 않는다(null → 화면이 목록을 다시 읽는다).
 */
export async function writtenOpportunity(
  written: SalesOpportunity | null | undefined,
  id: string | undefined,
): Promise<SalesOpportunity | null> {
  if (written?.opportunityId && written.modifiedAt) return written;
  if (!id) return null;
  try {
    return await getCommonSalesOpportunity(id);
  } catch {
    return null;
  }
}
export function salesOpportunityPayload(
  input: SalesOpportunityInput,
): SalesOpportunityInput {
  return {
    opportunityName: input.opportunityName.trim(),
    customerId: input.customerId,
    salesPartId: input.salesPartId,
    stageCode: input.stageCode,
    winningProbability: input.winningProbability,
    expectedAmount: input.expectedAmount,
    contractTypeCode: input.contractTypeCode || null,
    startMonth: input.startMonth,
    expectedMonths: input.expectedMonths || null,
    workplace: input.workplace || null,
    workAreaCode: input.workAreaCode || null,
    nextAction: input.nextAction || null,
    nextActionDueDate: input.nextActionDueDate || null,
    memo: input.memo || null,
    needs: input.needs.map((n) => ({
      ...n,
      gradeCode: n.gradeCode || null,
      estimatedUnitPrice: n.estimatedUnitPrice || null,
    })),
  };
}
export const postCommonSalesOpportunity = (input: SalesOpportunityInput) =>
  developerRequest<SalesOpportunity>(root, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(salesOpportunityPayload(input)),
  });
export const putCommonSalesOpportunity = (
  id: string,
  input: SalesOpportunityInput,
  modifiedAt: string,
) =>
  developerRequest<SalesOpportunity>(`${root}/${encodeURIComponent(id)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...salesOpportunityPayload(input), modifiedAt }),
  });
export type SalesOpportunityAction =
  | "approve"
  | "reject"
  | "win"
  | "lose"
  | "stop"
  | "resume";
export function salesOpportunityAction<T = SalesOpportunity>(
  row: SalesOpportunity,
  action: SalesOpportunityAction,
  values: Record<string, unknown> = {},
) {
  return developerRequest<T>(
    `${root}/${encodeURIComponent(row.opportunityId)}/${action}`,
    {
      method: action === "approve" || action === "reject" ? "PUT" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...values, modifiedAt: row.modifiedAt }),
    },
  );
}
export const SALES_QUOTE_URL = "/api/common/sales-pipeline/quote";
export const quoteCommonSalesOpportunity = (needs: SalesNeed[]) =>
  developerRequest<SalesQuote>(SALES_QUOTE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ needs }),
  });

/** 단계별 기본 확률(운영 기준값 STEP_PRBL_n의 이번 달 값). 파이프라인 조회의 stages[].defaultProbability에서 읽는다. */
export async function getSalesStageDefaults(): Promise<Record<string, number>> {
  const pipeline = await developerRequest<{
    stages?: { stageCode: string; defaultProbability?: number | null }[];
  }>("/api/common/sales-pipeline");
  return Object.fromEntries(
    (pipeline?.stages || [])
      .filter((s) => s.defaultProbability != null)
      .map((s) => [s.stageCode, Number(s.defaultProbability)]),
  );
}

/** 가배정 경고(SFR-129-08)를 물을 한 줄: 폼의 몇 번째 줄(rowIndex)에 누구를 넣었는지. */
export interface SalesTentativeLine {
  rowIndex: number;
  needSn?: number;
  developerId: string;
  startMonth: string;
}
export interface SalesTentativeWarning {
  rowIndex: number;
  developerId: string;
  message: string;
}
type RawWarning = {
  /** null = 묻은 그 줄, 숫자 = 같은 사람이 있는 이 기회의 저장된 다른 줄. */
  needSn: number | null;
  developerId: string;
  message: string;
};
/**
 * 저장 전 줄도 검사하는 서버 경고(다른 기회에 이미 가배정, 투입 배정으로 넘긴 후보, 그 달 1.0 MM 이상 투입).
 * 서버는 사람 한 명씩 묻는다: { developerId, startMonth, opportunityId? }(새 기회면 opportunityId를 뺀다).
 * 같은 사람·같은 달은 한 번만 묻는다. 한 요청이 실패해도 나머지 경고는 보이고, 저장은 막지 않는다.
 */
export const SALES_TENTATIVE_CHECK_URL = `${root}/tentative-assignment-check`;
export async function checkSalesTentativeWarnings(
  opportunityId: string | undefined,
  lines: SalesTentativeLine[],
): Promise<SalesTentativeWarning[]> {
  const groups = new Map<string, SalesTentativeLine[]>();
  for (const line of lines) {
    const key = `${line.developerId}|${line.startMonth}`;
    groups.set(key, [...(groups.get(key) || []), line]);
  }
  const results = await Promise.allSettled(
    [...groups.values()].map(async (group) => {
      const { developerId, startMonth } = group[0];
      const result = await developerRequest<{ warnings: RawWarning[] }>(
        SALES_TENTATIVE_CHECK_URL,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(
            opportunityId
              ? { developerId, startMonth, opportunityId }
              : { developerId, startMonth },
          ),
        },
      );
      const found = result?.warnings || [];
      return group.flatMap((line) => {
        // 숫자 needSn = 같은 사람이 이 기회의 저장된 다른 줄에도 있다는 경고(짝인 null 경고와 같은 문구).
        // 그 줄이 이 줄 자신이거나 폼에서 그 사람이 빠졌으면 서버가 아는 저장값이 낡은 것이라 짝까지 버린다.
        const stale = new Set(
          found
            .filter(
              (w) =>
                w.needSn != null &&
                (w.needSn === line.needSn ||
                  !lines.some(
                    (l) =>
                      l.needSn === w.needSn && l.developerId === w.developerId,
                  )),
            )
            .map((w) => w.message),
        );
        return found.flatMap((w) => {
          if (stale.has(w.message)) return [];
          const target =
            w.needSn == null
              ? line
              : lines.find(
                  (l) =>
                    l.needSn === w.needSn && l.developerId === w.developerId,
                );
          return target
            ? [
                {
                  rowIndex: target.rowIndex,
                  developerId: w.developerId,
                  message: w.message,
                },
              ]
            : [];
        });
      });
    }),
  );
  const seen = new Set<string>();
  return results
    .flatMap((r) => (r.status === "fulfilled" ? r.value : []))
    .filter((w) => {
      const key = `${w.rowIndex}|${w.developerId}|${w.message}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

const MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;
/** 「YYYY-MM」에 달 수를 더한 「YYYY-MM」. */
export function addMonths(month: string, count: number) {
  const [year, value] = month.split("-").map(Number);
  const total = year * 12 + value - 1 + count;
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, "0")}`;
}
/** 「YYYY-MM」 달의 말일 「YYYY-MM-DD」. */
export const lastDayOfMonth = (month: string) =>
  addDays(`${addMonths(month, 1)}-01`, -1);

const NEED_LABELS: Record<string, string> = {
  jobCode: "직무",
  requiredCount: "인원(1명 이상)",
  startMonth: "시작월",
  months: "개월(1 이상)",
  monthlyMm: "월 MM(0 초과 1.0 이하)",
  estimatedUnitPrice: "예상 단가",
};
/** 필요 인력 한 줄에서 틀린 칸(인원·개월 NUMERIC(3), 월 MM NUMERIC(4,2), 단가 NUMERIC(15)). */
export function salesNeedBadFields(n: SalesNeed): (keyof SalesNeed)[] {
  const bad: (keyof SalesNeed)[] = [];
  if (!n.jobCode) bad.push("jobCode");
  if (
    !Number.isInteger(n.requiredCount) ||
    n.requiredCount < 1 ||
    n.requiredCount > 999
  )
    bad.push("requiredCount");
  if (!MONTH.test(n.startMonth || "")) bad.push("startMonth");
  if (!Number.isInteger(n.months) || n.months < 1 || n.months > 999)
    bad.push("months");
  if (
    n.monthlyMm == null ||
    !Number.isFinite(n.monthlyMm) ||
    n.monthlyMm <= 0 ||
    n.monthlyMm > 1 ||
    Math.abs(Math.round(n.monthlyMm * 100) - n.monthlyMm * 100) > 1e-6
  )
    bad.push("monthlyMm");
  if (
    n.estimatedUnitPrice != null &&
    (!Number.isFinite(n.estimatedUnitPrice) ||
      n.estimatedUnitPrice <= 0 ||
      n.estimatedUnitPrice >= 1e15)
  )
    bad.push("estimatedUnitPrice");
  return bad;
}
/** 시안 oppErrors 문구. 필요 인력 줄은 「○번째 줄의 ○○ 칸을 확인해 주세요.」 한 줄(needs[i]). */
export function salesOpportunityErrors(
  input: SalesOpportunityInput,
): Record<string, string> {
  const errors: Record<string, string> = {};
  const name = (input.opportunityName || "").trim();
  if (!name) errors.opportunityName = "기회명을 입력해 주세요.";
  else if ([...name].length > 100)
    errors.opportunityName = "기회명은 100자 이내로 넣어 주세요.";
  if (!input.customerId) errors.customerId = "고객사를 골라 주세요.";
  if (!input.salesPartId) errors.salesPartId = "영업 파트를 골라 주세요.";
  if (!input.stageCode) errors.stageCode = "단계를 골라 주세요.";
  const p = input.winningProbability;
  if (
    p == null ||
    !Number.isFinite(p) ||
    p < 0 ||
    p > 100 ||
    Math.abs(Math.round(p * 100) - p * 100) > 1e-6
  )
    errors.winningProbability = "0~100 사이 숫자로 입력해 주세요.";
  if (
    !Number.isFinite(input.expectedAmount) ||
    input.expectedAmount <= 0 ||
    input.expectedAmount >= 1e15
  )
    errors.expectedAmount = "예상 금액을 입력해 주세요.";
  if (!MONTH.test(input.startMonth || ""))
    errors.startMonth = "시작 예정월을 골라 주세요.";
  if (
    input.expectedMonths != null &&
    (!Number.isInteger(input.expectedMonths) ||
      input.expectedMonths < 1 ||
      input.expectedMonths > 999)
  )
    errors.expectedMonths = "기간은 1개월 이상으로 넣어 주세요.";
  if ([...(input.workplace || "")].length > 200)
    errors.workplace = "근무지는 200자 이내로 넣어 주세요.";
  if ([...(input.nextAction || "")].length > 200)
    errors.nextAction = "할 일은 200자 이내로 넣어 주세요.";
  if ([...(input.memo || "")].length > 500)
    errors.memo = "비고는 500자 이내로 넣어 주세요.";
  if (!input.needs.length) errors.needs = "필요 인력을 1줄 이상 넣어 주세요.";
  input.needs.forEach((n, i) => {
    const bad = salesNeedBadFields(n).map((f) => NEED_LABELS[f]);
    if (bad.length)
      errors[`needs[${i}]`] =
        `${i + 1}번째 줄의 ${bad.join(", ")} 칸을 확인해 주세요.`;
  });
  return errors;
}
export function salesWinDefaults(row: SalesOpportunity) {
  return {
    contractAmount: row.expectedAmount,
    contractTypeCode:
      row.contractTypeCode &&
      SALES_CONTRACT_TYPES.includes(row.contractTypeCode)
        ? row.contractTypeCode
        : "TK",
    contractStartDate: `${row.startMonth}-01`,
    contractEndDate: lastDayOfMonth(
      addMonths(row.startMonth, (row.expectedMonths || 6) - 1),
    ),
    projectStatusCode: "ING",
  };
}

/** 시안 pct(): 소수 한 자리 %, 음수는 「−」, 없으면 「–」. */
export const formatPercent = (value: number | null | undefined) =>
  value == null || !Number.isFinite(Number(value))
    ? "–"
    : `${Number(value) < 0 ? "−" : ""}${Math.abs(Number(value)).toFixed(1)}%`;
/** 시안 mmf(): MM 소수 두 자리. */
export const formatMm = (value: number | null | undefined) =>
  value == null || !Number.isFinite(Number(value))
    ? "–"
    : Number(value).toFixed(2);
/** 기준값 숫자(15.0000 → 15). */
export const plainNumber = (value: number | null | undefined) =>
  value == null || !Number.isFinite(Number(value))
    ? "–"
    : String(Number(value));
/** 견적 판정(SFR-129-04) 배지 색: 손해 예상·승인 필요 빨강, 목표 미달 주황, 기준 충족 초록. */
export const quoteBandKind = (band: string | undefined) =>
  band === "손해 예상" || band === "승인 필요"
    ? ("bad" as const)
    : band === "목표 미달"
      ? ("warn" as const)
      : band === "기준 충족"
        ? ("ok" as const)
        : ("neutral" as const);
