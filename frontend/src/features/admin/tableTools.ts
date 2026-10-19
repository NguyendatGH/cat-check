import { classifyAdminError, type AdminErrorKind } from "./adminAccess";



export type SortDirection = "asc" | "desc";

export interface SortState<K extends string> {
  key: K;
  direction: SortDirection;
}

export type SortValue = string | number | boolean | null | undefined;

export function compareSortValues(a: SortValue, b: SortValue, direction: SortDirection): number {
  const aEmpty = a === null || a === undefined || a === "";
  const bEmpty = b === null || b === undefined || b === "";
  if (aEmpty || bEmpty) {
    return aEmpty === bEmpty ? 0 : aEmpty ? 1 : -1;
  }
  let result: number;
  if (typeof a === "number" && typeof b === "number") {
    result = a - b;
  } else if (typeof a === "boolean" && typeof b === "boolean") {
    result = Number(a) - Number(b);
  } else {
    result = String(a).localeCompare(String(b), "vi", { numeric: true, sensitivity: "base" });
  }
  return direction === "asc" ? result : -result;
}

export function sortRows<T, K extends string>(
  rows: readonly T[],
  sort: SortState<K> | null,
  accessor: (row: T, key: K) => SortValue,
): T[] {
  if (sort === null) {
    return [...rows];
  }
  return rows
    .map((row, index) => ({ row, index }))
    .sort((left, right) => {
      const diff = compareSortValues(accessor(left.row, sort.key), accessor(right.row, sort.key), sort.direction);
      return diff !== 0 ? diff : left.index - right.index;
    })
    .map((entry) => entry.row);
}

export function nextSort<K extends string>(current: SortState<K> | null, key: K): SortState<K> {
  if (current?.key !== key) {
    return { key, direction: "asc" };
  }
  return { key, direction: current.direction === "asc" ? "desc" : "asc" };
}

export function ariaSortOf<K extends string>(
  current: SortState<K> | null,
  key: K,
): "ascending" | "descending" | "none" {
  if (current?.key !== key) {
    return "none";
  }
  return current.direction === "asc" ? "ascending" : "descending";
}


export function normalizeSearchText(value: string): string {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

export function matchesQuery(fields: readonly (string | number | null | undefined)[], query: string): boolean {
  const terms = normalizeSearchText(query)
    .split(/\s+/)
    .filter((term) => term.length > 0);
  if (terms.length === 0) {
    return true;
  }
  const haystack = normalizeSearchText(
    fields
      .filter((field) => field !== null && field !== undefined)
      .map(String)
      .join(" "),
  );
  return terms.every((term) => haystack.includes(term));
}


export function toggleSelection(selected: ReadonlySet<string>, id: string): Set<string> {
  const next = new Set(selected);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  return next;
}

export function toggleAllVisible(selected: ReadonlySet<string>, visibleIds: readonly string[]): Set<string> {
  const next = new Set(selected);
  const allSelected = visibleIds.length > 0 && visibleIds.every((id) => next.has(id));
  for (const id of visibleIds) {
    if (allSelected) {
      next.delete(id);
    } else {
      next.add(id);
    }
  }
  return next;
}

export function selectionState(selected: ReadonlySet<string>, visibleIds: readonly string[]): boolean | "mixed" {
  const count = visibleIds.filter((id) => selected.has(id)).length;
  if (count === 0) {
    return false;
  }
  return count === visibleIds.length ? true : "mixed";
}

export function pruneSelection(selected: ReadonlySet<string>, availableIds: readonly string[]): Set<string> {
  const available = new Set(availableIds);
  return new Set([...selected].filter((id) => available.has(id)));
}


export interface BulkFailure {
  id: string;
  kind: AdminErrorKind;
  message: string;
}

export interface BulkOutcome {
  succeeded: string[];
  failed: BulkFailure[];
}

export async function runSequential<T>(
  tasks: readonly T[],
  idOf: (task: T) => string,
  worker: (task: T) => Promise<unknown>,
  onProgress?: (done: number, total: number) => void,
): Promise<BulkOutcome> {
  const outcome: BulkOutcome = { succeeded: [], failed: [] };
  let done = 0;
  for (const task of tasks) {
    const id = idOf(task);
    try {
      await worker(task);
      outcome.succeeded.push(id);
    } catch (error) {
      outcome.failed.push({
        id,
        kind: classifyAdminError(error),
        message: error instanceof Error ? error.message : "",
      });
    }
    done += 1;
    onProgress?.(done, tasks.length);
  }
  return outcome;
}

export function summarizeBulk(outcome: BulkOutcome): { ok: number; failed: number; stale: number } {
  return {
    ok: outcome.succeeded.length,
    failed: outcome.failed.length,
    stale: outcome.failed.filter((failure) => failure.kind === "stale").length,
  };
}
