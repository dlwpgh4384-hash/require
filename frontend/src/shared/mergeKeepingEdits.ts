/**
 * 409(다른 사람이 먼저 저장) 뒤 [최신 버전 불러오기 (입력 유지)]: 표의 줄을 최신 값과 합친다(2026-11-24 사용자 결정).
 * - 내가 고친 줄(읽었을 때 값과 다른 줄): 내 입력을 두고, 버전 칸만 최신으로.
 * - 내가 안 고친 줄: 최신 값 그대로(다른 사람이 바꾼 것을 옛 값으로 덮지 않게).
 * - 내가 새로 넣은 줄(읽었을 때 없던 줄): 그대로 뒤에 둔다. 최신에 없어진 줄은 뺀다.
 */
export function mergeKeepingEdits<T>(
  draft: readonly T[],
  snapshot: readonly T[],
  latest: readonly T[],
  key: (row: T) => string,
  version?: keyof T,
): T[] {
  const strip = (row: T) => {
    if (!version) return JSON.stringify(row);
    const copy = { ...(row as Record<string, unknown>) };
    delete copy[version as string];
    return JSON.stringify(copy);
  };
  const before = new Map(snapshot.map((row) => [key(row), row]));
  const mine = new Map(draft.map((row) => [key(row), row]));
  const merged = latest.map((row) => {
    const edited = mine.get(key(row));
    const original = before.get(key(row));
    if (!edited || !original || strip(edited) === strip(original)) return row;
    return version ? { ...edited, [version]: row[version] } : edited;
  });
  const known = new Set(latest.map(key));
  for (const row of draft) if (!before.has(key(row)) && !known.has(key(row))) merged.push(row);
  return merged;
}
