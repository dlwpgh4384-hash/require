const paths = {
  chevron: "m9 5 7 7-7 7",
  collapse: "m14 6-6 6 6 6",
  star: "m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9z",
  clock: "M12 8v4l3 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0",
  tabs: "M3 6h7l2 3h9v12H3z M3 6V3h18v3",
  tools: "M4 6h16 M4 12h16 M4 18h16 M8 3v6 M16 9v6 M10 15v6",
  keyboard: "M3 6h18v12H3z M6 10h1 M10 10h1 M14 10h1 M18 10h0 M7 14h10",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4",
  screen: "M3 4h18v13H3z M8 21h8 M12 17v4",
  alert:
    "M12 8v5 M12 17h.01 M10.3 3.9 2 18.2A1.2 1.2 0 0 0 3 20h18a1.2 1.2 0 0 0 1-1.8L13.7 3.9a2 2 0 0 0-3.4 0",
  empty: "M4 5h16l2 10v5H2v-5z M2 15h6l2 3h4l2-3h6",
  lock: "M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4",
  search: "M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
  refresh:
    "M20 7v5h-5 M4 17v-5h5 M5.6 6a8 8 0 0 1 13.7 2 M18.4 18A8 8 0 0 1 4.7 16",
};

export type ShellIconName = keyof typeof paths;

export function ShellIcon({
  name,
  className = "",
}: {
  name: ShellIconName;
  className?: string;
}) {
  return (
    <svg
      className={`shell-icon ${className}`}
      aria-hidden="true"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={paths[name]} />
    </svg>
  );
}
