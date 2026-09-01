import { useRef, useState } from "react";
import type { Page } from "../types";
import type { ListParams } from "./resources";
import { useCollection } from "./useCollection";

/** Server-side paginated + filtered list state. Filter changes are debounced. */
export function useListView<T>(
  loader: (params: ListParams) => Promise<Page<T>>,
  initialFilters: Record<string, string> = {}
) {
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<Record<string, string>>(initialFilters);
  const filterKey = JSON.stringify(filters);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const { data, loading, error, reload } = useCollection(
    () => loader({ page, size: 20, ...filters }),
    [page, filterKey]
  );

  function applyFilter(key: string, value: string) {
    setPage(0);
    setFilters((f) => {
      const next = { ...f };
      if (value) next[key] = value;
      else delete next[key];
      return next;
    });
  }

  function setFilter(key: string, value: string) {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => applyFilter(key, value), 300);
  }

  const rows = data?.content ?? [];
  return {
    rows,
    data,
    loading,
    error,
    reload,
    page,
    setPage,
    filters,
    setFilter,
    totalPages: data?.totalPages ?? 0,
    totalElements: data?.totalElements ?? 0
  };
}
