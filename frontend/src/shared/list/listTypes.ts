import type { ReactNode } from "react";

/** SFR-010 목록 공통: 열 정의. key는 정렬·열 설정·엑셀에서 같은 값을 쓴다. */
export interface ListColumn<T> {
  key: string;
  label: string;
  /** 열 설정에서 끌 수 없는 열(필수). */
  required?: boolean;
  /** 열 제목을 눌러 정렬할 수 있는지. 기본 true. */
  sortable?: boolean;
  /** 처음에는 숨겨 두는 참고 열. */
  hiddenByDefault?: boolean;
  /** 계산 열의 계산 방법(제목 옆 ⓘ). */
  calc?: string;
  align?: "left" | "right" | "center";
  render?: (row: T) => ReactNode;
}

export type SortDir = "asc" | "desc";
export interface ListSort {
  key: string;
  dir: SortDir;
}

export const LIST_PAGE_SIZES = [20, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 50;
export const listMessages = {
  empty: "조건에 맞는 결과가 없습니다.",
  loading: "목록을 불러오는 중입니다.",
  loadError: "목록을 불러오지 못했습니다. 다시 시도해 주세요.",
  downloadError: "엑셀 파일을 받지 못했습니다. 다시 시도해 주세요.",
} as const;
