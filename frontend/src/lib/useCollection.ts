import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "./api";

interface CollectionState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
}

/**
 * Runs an async loader on mount and whenever `deps` change, exposing a `reload` for use after
 * mutations. Out-of-order responses are ignored so rapid filter changes can't leave stale data.
 */
export function useCollection<T>(loader: () => Promise<T>, deps: unknown[] = []): CollectionState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const requestId = useRef(0);

  const run = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await loader();
      if (id === requestId.current) setData(result);
    } catch (err) {
      if (id === requestId.current) {
        setError(err instanceof ApiError ? err.message : "Something went wrong");
      }
    } finally {
      if (id === requestId.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    run();
  }, [run]);

  return { data, loading, error, reload: run };
}
