import { useEffect, useRef, useState } from "react";
import {
  developerFailure,
  getCommonDeveloperAreas,
  type DeveloperArea,
} from "../../pages/developers/developers";

/**
 * 행정구역 고르기(시·도 → 시·군·구 → 동, SFR-021). 개발자 거주지(UI-02)·후보 거주지(UI-33)·
 * 프로젝트 근무지(UI-01)·영업 기회 근무지(UI-29)가 같이 쓴다(2026-11-25).
 * 저장하는 값은 고른 마지막 단계의 코드(동까지 고르면 동, 시·군·구까지면 시·군·구, 시·도만이면 시·도)이고,
 * [수정]은 이 코드로 목록을 다시 채운다(글자를 잘라 쓰지 않음).
 */
export type AreaOption = DeveloperArea;

/** 법정동코드: 시·군·구 단계는 끝 5자리가 00000, 읍·면·동은 아니다(세종처럼 시·군·구가 없는 시·도 구분). */
export const isDong = (code: string) => code.slice(5) !== "00000";
/** 응답에 목록이 없으면 빈 목록으로 본다. */
const itemsOf = (response: { items?: AreaOption[] } | null | undefined) =>
  Array.isArray(response?.items) ? response.items : [];

export interface AreaPicker {
  /** 단계별 목록(0 시·도, 1 시·군·구, 2 동). */
  lists: AreaOption[][];
  /** 단계별 고른 코드(안 고르면 ""). */
  location: string[];
  /** 목록을 읽는 중. */
  busy: boolean;
  /** 목록을 못 읽었을 때 문구(없으면 ""). */
  error: string;
  /** 한 단계를 고른다. 아래 단계는 비우고 새 목록을 읽는다. 고른 마지막 단계의 코드(없으면 null)를 바로 돌려준다. */
  select: (level: number, value: string) => string | null;
  /** 저장된 코드(이력서에서 가져온 값 등)로 고르기를 다시 채운다. 시·도 목록은 다시 읽지 않는다. */
  show: (code: string | null | undefined) => void;
  /** 못 읽었을 때 [다시 시도]: 시·도 목록부터 다시 읽고 code(주지 않으면 처음 코드)로 채운다. */
  retry: (code?: string | null) => void;
}

/**
 * code: 처음 채울 저장 코드(바뀌면 다시 채움). levels: 3이면 시·도·시·군·구·동, 2면 시·도·시·군·구(후보 거주지).
 * 세 단계일 때 시·도 아래가 바로 동이면(세종) 시·군·구 칸을 건너뛰고 동 목록에 넣는다(SFR-021-01).
 */
export function useAreaPicker(code: string | null | undefined, levels: 2 | 3 = 3): AreaPicker {
  const blank = () => Array.from({ length: levels }, () => [] as AreaOption[]);
  const [lists, setLists] = useState<AreaOption[][]>(blank);
  const [location, setLocation] = useState<string[]>(() => Array<string>(levels).fill(""));
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const request = useRef(0);
  const roots = useRef<AreaOption[]>([]);
  const retryCode = useRef<string | null | undefined>(undefined);
  const current = useRef(location);
  current.current = location;

  const expand = async (value: string | null | undefined, root: AreaOption[]) => {
    const next = blank();
    next[0] = root;
    const selected = Array<string>(levels).fill("");
    if (!value) return { next, selected };
    selected[0] = root.find((a) => a.areaCode.slice(0, 2) === value.slice(0, 2))?.areaCode || "";
    if (!selected[0] || selected[0] === value) return { next, selected };
    const children = itemsOf(await getCommonDeveloperAreas(selected[0]));
    if (levels === 3 && children.length && children.every((a) => isDong(a.areaCode))) {
      next[2] = children;
      selected[2] = children.find((a) => a.areaCode === value)?.areaCode || "";
      return { next, selected };
    }
    next[1] = children;
    selected[1] = children.find((a) => a.areaCode.slice(0, 5) === value.slice(0, 5))?.areaCode || "";
    if (levels === 3 && selected[1] && selected[1] !== value) {
      next[2] = itemsOf(await getCommonDeveloperAreas(selected[1]));
      selected[2] = next[2].find((a) => a.areaCode === value)?.areaCode || "";
    }
    return { next, selected };
  };

  const fill = async (value: string | null | undefined, root: AreaOption[], ticket: number) => {
    try {
      const { next, selected } = await expand(value, root);
      if (ticket !== request.current) return;
      setLists(next);
      setLocation(selected);
    } catch (reason) {
      if (ticket === request.current) setError(developerFailure(reason));
    } finally {
      if (ticket === request.current) setBusy(false);
    }
  };

  useEffect(() => {
    const ticket = ++request.current;
    const value = retryCode.current !== undefined ? retryCode.current : code;
    retryCode.current = undefined;
    setBusy(true);
    setError("");
    getCommonDeveloperAreas()
      .then((response) => {
        if (ticket !== request.current) return;
        roots.current = itemsOf(response);
        return fill(value, roots.current, ticket);
      })
      .catch((reason) => {
        if (ticket !== request.current) return;
        setError(developerFailure(reason));
        setBusy(false);
      });
    return () => {
      ++request.current;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code, revision]);

  const select = (level: number, value: string) => {
    const ticket = ++request.current;
    const next = current.current.map((v, i) => (i === level ? value : i > level ? "" : v));
    setLocation(next);
    setLists((old) => old.map((list, i) => (i > level ? [] : list)));
    setError("");
    const chosen = [...next].reverse().find(Boolean) || null;
    if (!value || level === levels - 1) {
      setBusy(false);
      return chosen;
    }
    setBusy(true);
    getCommonDeveloperAreas(value)
      .then((response) => {
        const items = itemsOf(response);
        const skip = levels === 3 && level === 0 && items.length > 0 && items.every((a) => isDong(a.areaCode));
        if (ticket === request.current)
          setLists((old) =>
            old.map((list, i) => (skip ? (i === 2 ? items : i === 1 ? [] : list) : i === level + 1 ? items : list)),
          );
      })
      .catch((reason) => {
        if (ticket === request.current) setError(developerFailure(reason));
      })
      .finally(() => {
        if (ticket === request.current) setBusy(false);
      });
    return chosen;
  };

  const show = (value: string | null | undefined) => {
    const ticket = ++request.current;
    setBusy(true);
    setError("");
    void fill(value, roots.current, ticket);
  };

  const retry = (value?: string | null) => {
    retryCode.current = value;
    setRevision((v) => v + 1);
  };

  return { lists, location, busy, error, select, show, retry };
}

/** 위 단계를 아직 안 골랐거나 고를 목록이 없으면 그 칸을 잠근다(개발자 거주지 칸과 같은 규칙). */
export const areaLevelLocked = (picker: AreaPicker, level: number) =>
  level === 1 ? !picker.location[0] || picker.lists[1].length === 0 : level === 2 ? picker.lists[2].length === 0 : false;

/** 한 단계의 고르는 칸. 칸 이름·도움말·오류 문구는 쓰는 화면의 칸 틀에 둔다. */
export function AreaSelect({
  picker,
  level,
  id,
  testId,
  disabled,
  placeholder = "선택",
  invalid,
  className = "ls-in",
  onPick,
}: {
  picker: AreaPicker;
  level: number;
  id: string;
  testId: string;
  disabled: boolean;
  placeholder?: string;
  invalid?: boolean;
  /** 쓰는 화면의 입력 칸 모양(기본 공통 ls-in). */
  className?: string;
  onPick: (level: number, value: string) => void;
}) {
  return (
    <select
      id={id}
      data-testid={testId}
      className={className}
      aria-invalid={invalid}
      value={picker.location[level]}
      disabled={disabled}
      onChange={(e) => onPick(level, e.target.value)}
    >
      <option value="">{placeholder}</option>
      {(picker.lists[level] ?? []).map((a) => (
        <option key={a.areaCode} value={a.areaCode}>
          {a.areaName}
        </option>
      ))}
    </select>
  );
}
