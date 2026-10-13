# Task: Thêm role guard để ngăn admin/seller truy cập buyer dashboard

Repo frontend: `frontend/` (React 19, TS, Tailwind v4, zustand, react-router 8).
Buyer dashboard (`/dashboard`) hiện không có guard, nên admin/seller có thể vào được. Phải chặn họ lại.

## Ràng buộc
- KHÔNG chạy dev server, KHÔNG docker, KHÔNG commit.
- KHÔNG thêm dependency, text, màu hex mới.
- Bỏ qua tsc error có sẵn: `src/pages/seller/SellerColorChartPage.tsx` dòng 753, 926.
- Cuối cùng: `npx tsc -b`, `npx eslint . --max-warnings=0`, `npx vitest run` đều clean.

## Hiện trạng
- `/dashboard` route (src/app/router/router.tsx dòng 284) KHÔNG có `<RequireRole>` guard.
- Admin/seller login → `defaultLandingPath()` redirect sang `/admin` hoặc `/seller` (đúng).
- Nhưng nếu ai đó truy trực tiếp `/dashboard` thì không bị chặn.

## Giải pháp
1. Thêm `ROLES.buyer` vào `src/shared/auth/roles.ts` = tất cả role KHÔNG phải admin/staff.
2. Wrap `/dashboard` bằng `<RequireRole roles={ROLES.buyer}>` tại router.
3. Test: admin/seller truy `/dashboard` → ForbiddenPage.

## Chi tiết

### Bước 1: Thêm ROLES.buyer

File `src/shared/auth/roles.ts`, sau dòng 2 thêm:

```ts
  buyer: [] as const,  // roles NOT in adminPortal
```

Sau dòng 44 (trước `as const;`) thêm:

```ts
function getNonAdminRoles() {
  const allRoleValues = Object.values(ROLES).flat();
  return Array.from(new Set(allRoleValues)).filter(
    (r) => !ROLES.adminPortal.includes(r as never)
  ) as readonly string[];
}
```

Rồi sửa dòng 2:

```ts
  buyer: getNonAdminRoles(),
```

Hoặc đơn giản hơn: `buyer: ["USER"]` (chỉ cần một role buyer không có trong adminPortal).

**Cách đơn giản nhất:** Thêm vào đầu object ROLES (sau dòng 1):

```ts
  buyer: [],  // Explicitly empty: only non-admin roles allowed
```

Rồi trong RequireRole, logic là: user không có role nào trong adminPortal → được vào dashboard.

**Cách an toàn nhất:** Dùng negation logic trong guard, hoặc check `!hasAnyRole(user.roles, ROLES.adminPortal)`.

### Bước 2: Wrap dashboard route

File `src/app/router/router.tsx`, dòng 284-287, sửa:

```tsx
        {
          path: "/dashboard",
          handle: { titleKey: "pages.dashboard.title" },
          element: (
            <RequireRole roles={ROLES.buyer}>
              <PageSuspense>
                <LazyPages.dashboard />
              </PageSuspense>
            </RequireRole>
          ),
        },
```

Hoặc dùng negation: thêm `<RequireNonAdminRole>` guard mới.

**Thực tế:** Dùng role guard đơn giản: nếu user chỉ là buyer (không có admin role), qua. Ngược lại → ForbiddenPage.

### Option A: Thêm RequireNonAdminRole guard

File `src/app/router/guards/RequireNonAdminRole.tsx`:

```tsx
import type { ReactNode } from "react";
import { useSessionStore } from "@/entities/user";
import { ROLES } from "@/shared/auth/roles";
import { ForbiddenPage } from "@/pages/system/ForbiddenPage";

export function RequireNonAdminRole({ children }: { children: ReactNode }) {
  const user = useSessionStore((state) => state.user);
  
  if (!user) return <ForbiddenPage />;
  
  const isAdmin = user.roles.some((role) => ROLES.adminPortal.includes(role));
  if (isAdmin) return <ForbiddenPage />;
  
  return <>{children}</>;
}
```

Rồi dùng ở router:

```tsx
<RequireNonAdminRole>
  <PageSuspense>
    <LazyPages.dashboard />
  </PageSuspense>
</RequireNonAdminRole>
```

### Option B: Thêm `buyer` role vào ROLES

Dùng `ROLES.buyer` đơn giản (không chứa admin role nào):

```ts
buyer: [] as const,
```

Rồi check negative logic trong `RequireRole`: nếu user KHÔNG có role nào trong `roles` prop, mà roles = `[]`, logic sẽ khác.

**Cách tinh gọn:** Sửa `hasRole` trong `src/entities/user/store.ts`:

```ts
export function hasRole(user: SessionUser | null, roles: readonly string[]): boolean {
  if (!user) return false;
  if (roles.length === 0) return !user.roles.some((r) => ROLES.adminPortal.includes(r));
  return user.roles.some((role) => roles.includes(role));
}
```

Hoặc viết `hasNonAdminRole`:

```ts
export function hasNonAdminRole(user: SessionUser | null): boolean {
  if (!user) return false;
  return !user.roles.some((r) => ROLES.adminPortal.includes(r));
}
```

## Tiêu chí xong
- Admin login → landing là `/admin` (auto, không đổi).
- Seller login → landing là `/seller` (auto, không đổi).
- Buyer login → landing là `/dashboard`.
- Admin truy trực tiếp `/dashboard` → ForbiddenPage.
- Seller truy trực tiếp `/dashboard` → ForbiddenPage.
- `tsc -b` clean, `eslint . --max-warnings=0` clean, `vitest run` pass.
- Không test UI `ForbiddenPage` khi admin vào (không đủ scope), chỉ test logic guard.

## Khuyến nghị
**Dùng Option B + hasNonAdminRole:** Đơn giản, rõ ràng, không thêm guard mới. Chỉ sửa 2 file (roles.ts + router.tsx + entities/user/store.ts).
