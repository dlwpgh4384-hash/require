const paths: Record<string, string> = {
  grid: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  db: "M4 6c0-4 16-4 16 0s-16 4-16 0 M4 6v12c0 4 16 4 16 0V6 M4 12c0 4 16 4 16 0",
  folder: "M3 7V4h7l2 3h9v13H3z",
  calendar: "M3 5h18v16H3z M7 3v4 M17 3v4 M3 10h18",
  receipt: "M5 3h14v18l-3-2-4 2-4-2-3 2z M8 7h8 M8 11h8 M8 15h5",
  chart: "M3 3v18h18 M7 16v-5 M12 16V7 M17 16V4",
  wallet: "M3 6h18v15H3z M3 6V3h15v3 M16 11h5v5h-5z",
  lock: "M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4",
  sparkle: "M12 2l3 7 7 3-7 3-3 7-3-7-7-3 7-3z",
  gear: "M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
};

export function NavigationMenuIcon({ name }: { name: string | null }) {
  return (
    <span aria-hidden="true" className="menu-icon">
      {name && (
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d={paths[name] ?? "M4 4h16v16H4z M9 9h6v6H9z"} />
        </svg>
      )}
    </span>
  );
}
