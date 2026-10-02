import { Navigate } from "react-router";
import { useCatStore } from "@/entities/cat";

/**
 * `/history` là route redirect-only → `/cats/:activeCatId/history` (p9 §9.4.3).
 * M0 chưa có logic "mèo đang active" thật (chưa nối API /cats) — activeCatId luôn null,
 * nên tạm redirect về /cats để người dùng tự chọn mèo. TODO: nối entities/cat thật.
 */
export function HistoryRedirectPage() {
  const activeCatId = useCatStore((state) => state.activeCatId);

  if (activeCatId) {
    return <Navigate to={`/cats/${activeCatId}/history`} replace />;
  }
  return <Navigate to="/cats" replace />;
}
