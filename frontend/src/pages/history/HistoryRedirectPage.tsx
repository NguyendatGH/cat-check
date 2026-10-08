import { Navigate } from "react-router";
import { useCatStore } from "@/entities/cat";
import { useCatList } from "@/features/cat";
import { SkeletonLoader } from "@/shared/ui";

/**
 * `/history` là route redirect-only → `/cats/:activeCatId/history` (p9 §9.4.3).
 *
 * `activeCatId` nằm trong localStorage nên có thể CŨ (bé đã xoá/lưu trữ, hoặc của tài khoản
 * đăng nhập trước trên cùng máy) — chỉ dùng khi nó còn trong danh sách ACTIVE của tài khoản,
 * nếu không thì rơi về mèo mặc định (`isPrimary`) rồi bé đầu tiên. Không còn bé nào hoặc danh
 * sách không tải được thì về `/cats`.
 */
export function HistoryRedirectPage() {
  const activeCatId = useCatStore((state) => state.activeCatId);
  const { data, isPending, isError } = useCatList("ACTIVE");

  if (isPending) {
    return <SkeletonLoader shape="card" className="m-4 h-24" />;
  }

  const cats = isError ? [] : data.items;
  const target =
    cats.find((cat) => cat.id === activeCatId) ?? cats.find((cat) => cat.isPrimary) ?? cats.at(0) ?? null;

  if (target) {
    return <Navigate to={`/cats/${target.id}/history`} replace />;
  }

  return <Navigate to="/cats" replace />;
}
