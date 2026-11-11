import { useEffect, useState } from "react";

// FR-0107 화면 폭: 휴대폰 767px 이하, 태블릿 768~1279px, PC 1280px 이상.
export const MOBILE_QUERY = "(max-width: 767px)";
export const TABLET_QUERY = "(min-width: 768px) and (max-width: 1279px)";

function read(query: string) {
  return typeof window !== "undefined" && typeof window.matchMedia === "function"
    ? window.matchMedia(query).matches
    : false;
}

export function useViewport() {
  const [state, setState] = useState(() => ({
    mobile: read(MOBILE_QUERY),
    tablet: read(TABLET_QUERY),
  }));
  useEffect(() => {
    const queries = [MOBILE_QUERY, TABLET_QUERY].map((query) =>
      window.matchMedia(query),
    );
    const change = () =>
      setState({ mobile: read(MOBILE_QUERY), tablet: read(TABLET_QUERY) });
    queries.forEach((media) => media.addEventListener("change", change));
    return () =>
      queries.forEach((media) => media.removeEventListener("change", change));
  }, []);
  return state;
}
