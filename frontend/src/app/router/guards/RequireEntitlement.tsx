import type { ReactNode } from "react";
import { UpsellPage } from "@/pages/system/UpsellPage";

export interface RequireEntitlementProps {
  /** Tên entitlement cần có (khớp mã gói ở BE). TODO: đồng bộ danh sách thật khi có API packages. */
  entitlement: string;
  children: ReactNode;
}

/**
 * Guard bước 5 (p9 mục 6): thiếu entitlement (gói không có tính năng) → UpsellPage TẠI CHỖ
 * trong layout của route đó — khác UI với ForbiddenPage (thiếu role) và redirect login
 * (chưa đăng nhập).
 *
 * TODO M0: spec trích cho milestone này KHÔNG nêu bảng ánh xạ route ↔ entitlement cụ thể
 * (đến từ p4/gói dịch vụ), nên guard này CHƯA được áp cho route nào trong router.tsx để
 * tránh bịa quy tắc nghiệp vụ — luôn cho qua (return children) ở M0. Người phụ trách
 * business logic nối bảng ánh xạ thật ở milestone sau.
 */
/** TODO: đọc từ entities/user hoặc packages API thật — trả kiểu `boolean` (không literal) để
 * ArchUnit/eslint không suy ra hằng số tĩnh và cảnh báo `no-unnecessary-condition` bên dưới. */
function checkHasEntitlement(): boolean {
  return true;
}

export function RequireEntitlement({ children }: RequireEntitlementProps) {
  const hasEntitlement = checkHasEntitlement();

  if (!hasEntitlement) {
    return <UpsellPage />;
  }

  return <>{children}</>;
}
