import type { SVGProps } from "react";

const paths = {
  panel: "M3 4h18v16H3z M9 4v16 M5.5 8h1 M5.5 12h1",
  chevron: "m9 6 6 6-6 6",
  star: "m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z",
  history: "M3 11a9 9 0 1 1 2.6 7.4 M3 4v7h7 M12 7v5l3 2",
  tools: "M4 7h16 M4 17h16 M8 4v6 M16 14v6",
  keyboard: "M3 5h18v14H3z M7 9h.01 M12 9h.01 M17 9h.01 M7 13h.01 M10 15h7",
  bell: "M5 17h14l-2-3V9a5 5 0 0 0-10 0v5z M10 21h4",
  tabs: "M3 5h7l2 3h9v12H3z",
  clock: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M12 7v5l3 2",
  alert: "m12 3 10 18H2z M12 9v5 M12 17h.01",
  empty: "M3 4h18v16H3z M3 14h5l2 3h4l2-3h5",
  monitor: "M3 3h18v14H3z M12 17v4 M8 21h8",
  unavailable: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0 M6 6l12 12",
  search: "M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0 M15 15l6 6",
  retry: "M20 7a9 9 0 1 0 1 8 M20 3v5h-5",
  arrow: "M4 12h16 m-6-6 6 6-6 6",
};

export function InterfaceIcon({
  name,
  className = "",
  ...props
}: SVGProps<SVGSVGElement> & { name: keyof typeof paths }) {
  return (
    <svg
      {...props}
      className={`interface-icon ${className}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
}
