const paths = {
  chevron: "m9 5 7 7-7 7",
  chevronDown: "M6 9l6 6 6-6",
  x: "M18 6L6 18M6 6l12 12",
  collapse: "m14 6-6 6 6 6",
  // 아래 아이콘은 화면 시안 v1.8과 같은 모양
  star: "M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3l-5.5 2.9 1-6.2L3 9.6l6.2-.9z",
  clock: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2",
  tabs: "M3 6h7l2 3h9v12H3z M3 6V3h18v3",
  tools: "M4 6h16 M4 12h16 M4 18h16 M8 3v6 M16 9v6 M10 15v6",
  keyboard: "M3 6h18v12H3z M6 10h1 M10 10h1 M14 10h1 M18 10h0 M7 14h10",
  bell: "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10 21h4",
  screen: "M3 4h18v13H3z M8 21h8 M12 17v4",
  alert:
    "M12 8v5 M12 17h.01 M10.3 3.9 2 18.2A1.2 1.2 0 0 0 3 20h18a1.2 1.2 0 0 0 1-1.8L13.7 3.9a2 2 0 0 0-3.4 0",
  empty: "M4 5h16l2 10v5H2v-5z M2 15h6l2 3h4l2-3h6",
  lock: "M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4",
  search: "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-4.3-4.3",
  spark: "M12 3l1.8 4.6L18 9l-4.2 1.4L12 15l-1.8-4.6L6 9l4.2-1.4zM19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z",
  user: "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0",
  refresh:
    "M20 7v5h-5 M4 17v-5h5 M5.6 6a8 8 0 0 1 13.7 2 M18.4 18A8 8 0 0 1 4.7 16",
};

export type ShellIconName = keyof typeof paths;

export function ShellIcon({
  name,
  className = "",
  size = 20,
}: {
  name: ShellIconName;
  className?: string;
  size?: number;
}) {
  return (
    <svg
      className={`shell-icon ${className}`}
      aria-hidden="true"
      width={size}
      height={size}
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
