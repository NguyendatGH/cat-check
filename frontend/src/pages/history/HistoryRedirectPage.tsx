import { Navigate } from "react-router";
import { useCatStore } from "@/entities/cat";
import { useCatList } from "@/features/cat";
import { SkeletonLoader } from "@/shared/ui";

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
