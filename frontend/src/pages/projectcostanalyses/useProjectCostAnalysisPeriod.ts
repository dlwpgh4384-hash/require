import { useEffect, useRef, useState } from "react";
import { koreanToday } from "../../shared/time";
import {
  getCommonUserPreference,
  putCommonUserPreference,
} from "../userpreferences/userPreferences";
import type { ProjectCostAnalysisPeriod } from "./projectCostAnalyses";

export function validProjectCostAnalysisPeriod(
  value: unknown,
  earliestYear: number,
): value is ProjectCostAnalysisPeriod {
  if (!value || typeof value !== "object") return false;
  const period = value as ProjectCostAnalysisPeriod;
  const today = koreanToday();
  const year = Number(today.slice(0, 4)),
    month = Number(today.slice(5, 7));
  return (
    Number.isInteger(period.year) &&
    period.year >= earliestYear &&
    period.year <= year &&
    Number.isInteger(period.baseMonth) &&
    period.baseMonth >= 1 &&
    period.baseMonth <= (period.year === year ? month : 12)
  );
}
export function useProjectCostAnalysisPeriod() {
  const [period, setPeriod] = useState<ProjectCostAnalysisPeriod>({
    year: Number(koreanToday().slice(0, 4)),
    baseMonth: Number(koreanToday().slice(5, 7)),
  });
  const [earliest, setEarliest] = useState(Number(koreanToday().slice(0, 4)));
  const [ready, setReady] = useState(false);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const token = useRef<string | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setReady(false);
    setError("");
    getCommonUserPreference("PERIOD", "UI-04", controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        const basis = response.meta.periodBasis;
        if (!basis)
          throw new Error(
            response.meta.periodBasisError ?? "기간 기준을 확인할 수 없습니다.",
          );
        const earliestYear = Number(basis.cashBaseYm.slice(0, 4));
        if (!Number.isInteger(earliestYear))
          throw new Error("기간 기준을 확인할 수 없습니다.");
        setEarliest(earliestYear);
        token.current = response.data.modDttm;
        let saved: unknown;
        try {
          const value = JSON.parse(response.data.prefVal ?? "null");
          saved = value && {
            year: value.year,
            baseMonth: value.baseMonth ?? value.month,
          };
        } catch {
          saved = null;
        }
        setPeriod(
          validProjectCostAnalysisPeriod(saved, earliestYear)
            ? saved
            : {
                year: Number(koreanToday().slice(0, 4)),
                baseMonth: Number(koreanToday().slice(5, 7)),
              },
        );
        setReady(true);
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setError(
            failure instanceof Error
              ? failure.message
              : "기간을 불러오지 못했습니다.",
          );
      });
    return () => controller.abort();
  }, [reload]);
  function change(value: ProjectCostAnalysisPeriod) {
    if (!validProjectCostAnalysisPeriod(value, earliest)) return;
    setPeriod(value);
    void putCommonUserPreference(
      "PERIOD",
      "UI-04",
      JSON.stringify({ year: value.year, month: value.baseMonth }),
      token.current,
    )
      .then((response) => {
        token.current = response.data.modDttm;
      })
      .catch(() =>
        setError("기간은 변경되었지만 기간 설정을 저장하지 못했습니다."),
      );
  }
  return {
    period,
    earliest,
    ready,
    error,
    change,
    retry: () => setReload((value) => value + 1),
  };
}
