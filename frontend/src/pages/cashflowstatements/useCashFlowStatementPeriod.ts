import { useEffect, useState } from "react";
import { useScreenPeriod } from "../userpreferences/useScreenPeriod";
import { getCommonUserPreference } from "../userpreferences/userPreferences";

export const cashFlowPeriodOptions = { kind: "year" } as const;
export function useCashFlowStatementPeriod() {
  const period = useScreenPeriod("UI-21", cashFlowPeriodOptions);
  const [fallback, setFallback] = useState<{
    year: number;
    currentYear: number;
    currentMonth: number;
  } | null>(null);
  useEffect(() => {
    if (period.state.status !== "error") {
      setFallback(null);
      return;
    }
    const controller = new AbortController();
    getCommonUserPreference("PERIOD", "UI-21", controller.signal)
      .then((response) => {
        const current = response.meta.currentYearMonth;
        if (
          controller.signal.aborted ||
          response.meta.periodBasis ||
          !current?.match(/^[0-9]{4}(0[1-9]|1[0-2])$/)
        )
          return;
        const year = Number(current.slice(0, 4));
        setFallback({
          year,
          currentYear: year,
          currentMonth: Number(current.slice(4)),
        });
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [period.state.status]);
  return {
    ...period,
    fallback,
    setFallbackYear: (year: number) =>
      setFallback((previous) => (previous ? { ...previous, year } : previous)),
  };
}
