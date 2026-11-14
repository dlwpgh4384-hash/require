import { getCommonCodes } from "../codegroups/codeGroups";

export interface Client {
  clientId: string;
  clientTypeCode: string;
  clientName: string;
  businessNumber: string;
  representativeName: string;
  industryCode: string;
  contactName: string;
  contactPhone: string;
  taxEmail: string;
  bankCode: string;
  paymentAccountSummary: string;
  useYn: string;
  modifiedAt: string | null;
  projectCount: number;
  personCount: number;
}
export interface ClientInput {
  clientTypeCode: string;
  clientName: string;
  businessNumber?: string;
  representativeName?: string;
  industryCode?: string;
  contactName?: string;
  contactPhone?: string;
  taxEmail?: string;
  bankCode?: string;
  accountNumber?: string;
  accountHolder?: string;
  modifiedAt?: string | null;
}
export interface ClientAccount {
  bankCode: string;
  accountNumber: string;
  accountHolder: string;
}
export interface ClientAssignment {
  projectId: string;
  projectName: string;
  personName: string;
  companyName: string;
  jobName: string;
  withdrawalDate: string | null;
}
export interface ClientChange {
  changedAt: string;
  fieldName: string;
  beforeValue: string | null;
  afterValue: string | null;
  changedBy: string;
}
export interface ClientCode {
  code: string;
  codeName: string;
}
export class ClientRequestError extends Error {
  constructor(
    public status: number,
    message: string,
    public errors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  const body = await response.json();
  if (!response.ok || body.success === false)
    throw new ClientRequestError(
      response.status,
      body.message || "요청하지 못했습니다.",
      body.errors || [],
    );
  return body.data as T;
}
const root = "/api/common/clients";
const target = (id: string) => `${root}/${encodeURIComponent(id)}`;
export const getCommonClients = (
  filter: { clientTypeCode?: string; useYn?: string; keyword?: string } = {},
  signal?: AbortSignal,
) =>
  request<{ items: Client[] }>(
    `${root}?${new URLSearchParams(Object.entries(filter).filter(([, value]) => !!value) as [string, string][])}`,
    { signal },
  );
export const getCommonClient = (id: string) => request<Client>(target(id));
export const getCommonClientAssignments = (id: string) =>
  request<{ items: ClientAssignment[] }>(`${target(id)}/assignments`);
export const getCommonClientChangeHistory = (id: string) =>
  request<{ items: ClientChange[] }>(`${target(id)}/change-history`);
export const getCommonClientPaymentAccount = (id: string) =>
  request<ClientAccount>(`${target(id)}/payment-account`);
const save = (path: string, method: string, body: unknown) =>
  request<{ client: Client; warnings: string[] }>(path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
export const postCommonClient = (input: ClientInput) =>
  save(root, "POST", input);
export const putCommonClient = (id: string, input: ClientInput) =>
  save(target(id), "PUT", input);
export const putCommonClientUseStatus = (
  items: { clientId: string; modifiedAt: string | null }[],
  useYn: string,
) =>
  request<{
    changed: string[];
    failed: { clientId: string; reason: string }[];
  }>(`${root}/use-status`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items, useYn }),
  });
export const getClientCodes = async (groupId: string, signal?: AbortSignal) => {
  const result = await getCommonCodes(groupId, signal);
  return { items: result.items.filter((code) => code.useYn !== "N") };
};
export function clientPayload(
  draft: ClientInput,
  editing: boolean,
): ClientInput {
  const result = { ...draft };
  if (editing) delete result.businessNumber;
  else delete result.modifiedAt;
  if (draft.clientTypeCode === "CUST") {
    delete result.bankCode;
    delete result.accountNumber;
    delete result.accountHolder;
  }
  if (draft.clientTypeCode === "VNDR") delete result.taxEmail;
  return Object.fromEntries(
    Object.entries(result).filter(([, value]) => value !== ""),
  ) as unknown as ClientInput;
}
export function formatBusinessNumber(value: string) {
  const digits = value.replace(/\D/g, "").slice(0, 10);
  return [digits.slice(0, 3), digits.slice(3, 5), digits.slice(5)]
    .filter(Boolean)
    .join("-");
}
