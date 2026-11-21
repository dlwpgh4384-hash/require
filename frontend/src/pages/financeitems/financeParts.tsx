import { useLayoutEffect, useRef, type ReactNode } from "react";
import { formatMoney } from "../../shared/list/moneyUnit";
import type { ToastKind } from "../../shared/feedback/Dialog";
import type { Amount } from "./financeItems";

/** 칸 오류(서버 errors[].field 또는 화면 검사) — 이름은 `items[2].amount`처럼 줄 번호를 붙인다. */
export type Fields = Record<string, string>;
export type Register = (
  name: string,
) => (element: HTMLElement | null) => void;
export type Notify = (message: string, kind?: ToastKind) => void;

/** 시안 won(): 쉼표, 음수 「−」, 빈 값 「–」(공통 formatMoney). */
export const won = (value: Amount | null | undefined) =>
  formatMoney(
    value === "" || value == null ? null : Number(value),
    "원",
  );
/** 조정 금액처럼 부호를 붙여 보인다(+1,000 / −1,000). */
export const signed = (value: number) =>
  `${value > 0 ? "+" : value < 0 ? "−" : ""}${won(Math.abs(value))}`;
/** 「YYYYMM」 → 「YYYY-MM」. */
export const dash = (month: string) =>
  month.length === 6 ? `${month.slice(0, 4)}-${month.slice(4)}` : month;

const paths = {
  info: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16v-4M12 8h.01",
  lock: "M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4",
  plus: "M12 5v14M5 12h14",
  down: "M12 3v12M7 10l5 5 5-5M5 21h14",
  chevL: "M15 18l-6-6 6-6",
  chevR: "M9 6l6 6-6 6",
  x: "M18 6L6 18M6 6l12 12",
};
/** 시안 ic(): 선 아이콘. */
export function Icon({
  name,
  size = 16,
}: {
  name: keyof typeof paths;
  size?: number;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
}

/** 필수 표시(시안 <span class="bad">*</span>). */
export const Req = () => <span className="fin-req"> *</span>;

export function FieldError({
  fields,
  name,
}: {
  fields: Fields;
  name: string;
}) {
  return fields[name] ? (
    <small role="alert" className="fin-err">
      {fields[name]}
    </small>
  ) : null;
}

/**
 * 시안 .money 칸: 쓰는 동안 쉼표를 붙여 보이고(커서는 끝에서 같은 자리), 값은 숫자 글자만 보관한다.
 * 빈칸은 쓰는 중 그대로 두고, blankToZero면 칸을 벗어날 때 0으로 바꾼다.
 */
export function MoneyInput({
  value,
  onChange,
  onBlur,
  onFocus,
  label,
  width,
  placeholder,
  invalid = false,
  inputRef,
  id,
  testId,
  readOnly = false,
  disabled = false,
  blankToZero = false,
  strong = false,
}: {
  value: Amount | null | undefined;
  onChange: (digits: string) => void;
  onBlur?: () => void;
  onFocus?: () => void;
  label?: string;
  width?: number;
  placeholder?: string;
  invalid?: boolean;
  inputRef?: (element: HTMLElement | null) => void;
  id?: string;
  testId?: string;
  readOnly?: boolean;
  disabled?: boolean;
  blankToZero?: boolean;
  strong?: boolean;
}) {
  const own = useRef<HTMLInputElement | null>(null);
  const caret = useRef<number | null>(null);
  const shown =
    value === "" || value == null ? "" : formatMoney(Number(value), "원");
  useLayoutEffect(() => {
    const element = own.current;
    if (caret.current == null || !element || document.activeElement !== element)
      return;
    const at = Math.max(0, element.value.length - caret.current);
    element.setSelectionRange?.(at, at);
    caret.current = null;
  }, [shown]);
  return (
    <input
      ref={(element) => {
        own.current = element;
        inputRef?.(element);
      }}
      id={id}
      data-testid={testId}
      className="ls-in ls-in-sm fin-money"
      inputMode="numeric"
      aria-label={label}
      aria-invalid={invalid || undefined}
      placeholder={placeholder}
      readOnly={readOnly}
      disabled={disabled}
      style={{
        width,
        fontWeight: strong ? 600 : undefined,
      }}
      value={shown}
      onFocus={onFocus}
      onChange={(event) => {
        const element = event.target;
        caret.current =
          element.value.length - (element.selectionStart ?? element.value.length);
        onChange(element.value.replace(/[^\d]/g, ""));
      }}
      onBlur={() => {
        if (blankToZero && (value === "" || value == null)) onChange("0");
        onBlur?.();
      }}
    />
  );
}

/** 시안 .card: 머리(제목 + 회색 설명) + 본문. */
export function Card({
  title,
  sub,
  extra,
  right,
  children,
  id,
  testId,
}: {
  title: string;
  sub?: string;
  extra?: ReactNode;
  right?: ReactNode;
  children: ReactNode;
  id?: string;
  testId?: string;
}) {
  return (
    <section className="fin-card" id={id} data-testid={testId}>
      <div className="fin-card-h">
        <h2>
          {title}{" "}
          {sub && <span className="ls-cap fin-card-sub">{sub}</span>}
          {extra}
        </h2>
        {right}
      </div>
      {children}
    </section>
  );
}

/** 표 아래 빈 줄(시안: colspan 가운데 회색 글). */
export function EmptyRow({ span, text }: { span: number; text: string }) {
  return (
    <tr>
      <td colSpan={span} className="fin-empty">
        {text}
      </td>
    </tr>
  );
}
