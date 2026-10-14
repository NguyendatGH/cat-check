import { useEffect, type ReactNode } from "react";
import { loadSession, useSessionStore } from "@/entities/user";
import { usePushSubscriptionRefresh } from "@/features/notification";

export function SessionProvider({ children }: { children: ReactNode }) {
  const setSession = useSessionStore((state) => state.setSession);
  const signedIn = useSessionStore((state) => state.user !== null);
  usePushSubscriptionRefresh(signedIn);

  useEffect(() => {
    let cancelled = false;
    void loadSession().then((user) => {
      if (!cancelled) setSession(user);
    });
    return () => {
      cancelled = true;
    };
  }, [setSession]);

  return <>{children}</>;
}
