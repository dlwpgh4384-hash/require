import { projectRequest } from "../projects/projects";
import { getCommonCodes } from "../codegroups/codeGroups";
import { getCommonClients } from "../clients/clients";
import { getCommonParts } from "../parts/parts";
import type {
  PersonalCostProfitFilter,
  PersonalCostProfitResult,
  PersonalCostProfitOptions,
} from "./personalCostProfitTypes";

const root = "/api/common/personal-cost-profits";
export function personalCostProfitQuery(filter: PersonalCostProfitFilter) {
  const params = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== undefined && value !== "")
      params.set(key, Array.isArray(value) ? value.join(",") : String(value));
  });
  return params;
}
export function getCommonPersonalCostProfits(
  filter: PersonalCostProfitFilter,
  signal?: AbortSignal,
) {
  return projectRequest<PersonalCostProfitResult>(
    `${root}?${personalCostProfitQuery(filter)}`,
    { signal },
  );
}
export async function getPersonalCostProfitOptions(
  signal?: AbortSignal,
): Promise<PersonalCostProfitOptions> {
  const [employees, jobs, clients, parts] = await Promise.all([
    getCommonCodes("EMP_TYPE", signal),
    getCommonCodes("JOB", signal),
    getCommonClients({ useYn: "Y" }, signal),
    getCommonParts(signal),
  ]);
  const codes = (items: typeof employees.items) =>
    (items ?? [])
      .filter((c) => c.useYn === "Y")
      .map((c) => ({ value: c.code, label: c.codeName }));
  return {
    employeeTypeCode: codes(employees.items),
    jobCode: codes(jobs.items),
    affiliationId: (clients.items ?? [])
      .filter((c) => c.useYn === "Y")
      .map((c) => ({ value: c.clientId, label: c.clientName })),
    salesPartId: (parts.items ?? [])
      .filter((p) => p.useYn === "Y")
      .map((p) => ({ value: p.partId, label: p.partName })),
  };
}
export async function getCommonPersonalCostProfitsExcel(
  filter: PersonalCostProfitFilter,
) {
  const response = await fetch(
    `${root}/excel?${personalCostProfitQuery(filter)}`,
  );
  const mime =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  if (!response.ok || !response.headers.get("Content-Type")?.includes(mime)) {
    let message = "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.";
    try {
      message = (await response.json()).message || message;
    } catch {
      /* non-JSON failure */
    }
    throw new Error(message);
  }
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
  const count = response.headers.get("X-Total-Count");
  if (!count || !/^\d+$/.test(count))
    throw new Error("조회 결과 건수를 확인하지 못했습니다.");
  const href = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = href;
  link.download = encoded
    ? decodeURIComponent(encoded)
    : plain || "개인 원가·수익표.xlsx";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(href);
  return Number(count);
}
