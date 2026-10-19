import { QueryClient } from "@tanstack/react-query";
import { isApiError } from "./errors";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // PERF: Increase staleTime to 60s to reduce unnecessary refetches during page load
      // Keep data fresh for 1 minute before marking as stale
      staleTime: 60_000,
      // Cache data for 5 minutes in memory to avoid refetching on route changes
      gcTime: 5 * 60 * 1000,
      // Reduce retry delay and attempt count for faster timeout
      retry: (failureCount, error) => {
        if (isApiError(error) && error.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount < 1;
      },
      // Don't refetch on window focus during critical page load
      refetchOnWindowFocus: false,
      // Don't refetch when re-mounting quickly (e.g., during route transitions)
      refetchOnReconnect: false,
    },
    mutations: {
      retry: false,
    },
  },
});
