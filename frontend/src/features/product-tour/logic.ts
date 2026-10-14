import { LANDING_PATHS } from "@/shared/auth/landing";
import type { FeatureFlagKey } from "@/shared/config/featureFlags";
import type { SessionUser } from "@/entities/user";
import { hasNonAdminRole } from "@/entities/user";
import type { TourStatus } from "./storage";
import { WELCOME_STEP, type TourPlacement, type TourStepDef } from "./steps";

export interface TourStartInput {
  user: SessionUser | null;
  pathname: string;
  /** Pha của brand splash; tour chỉ chạy khi splash đã ẩn hẳn. */
  splashPhase: string;
  status: TourStatus;
}

/** Tour chỉ tự chạy cho tài khoản người mua đã xong onboarding, đang ở Trang chủ, chưa xem/tắt. */
export function shouldAutoStartTour({ user, pathname, splashPhase, status }: TourStartInput): boolean {
  if (!user) return false;
  if (!hasNonAdminRole(user)) return false;
  if (!user.onboarding.completed) return false;
  if (splashPhase !== "hidden") return false;
  if (pathname.replace(/\/+$/, "") !== LANDING_PATHS.buyer) return false;
  return status === "pending";
}

/** Đóng giữa chừng hay hoàn tất đều đánh dấu đã xem; tick "Không hiển thị lại" => disabled. */
export function statusAfterClose(dontShowAgain: boolean): TourStatus {
  return dontShowAgain ? "disabled" : "seen";
}

export interface ResolvedTourStep {
  id: string;
  /** null => bước giữa màn hình (chào mừng). */
  target: string | null;
  titleKey: string;
  bodyKey: string;
  placement?: TourPlacement;
}

export interface ResolveTourOptions {
  isDesktop: boolean;
  flags: Record<FeatureFlagKey, boolean>;
  /** Trả về true khi phần tử `data-tour` đang hiển thị trên màn hình. */
  hasTarget: (target: string) => boolean;
}

/**
 * Chọn bộ bước theo breakpoint, bỏ bước có feature flag tắt hoặc phần tử không render.
 * Không còn bước nào gắn phần tử => trả mảng rỗng (không chạy tour).
 */
export function resolveTourSteps(defs: readonly TourStepDef[], options: ResolveTourOptions): ResolvedTourStep[] {
  const steps: ResolvedTourStep[] = [];
  for (const def of defs) {
    if (def.flag && !options.flags[def.flag]) continue;
    const variant = options.isDesktop ? def.desktop : def.mobile;
    if (!variant) continue;
    if (!options.hasTarget(variant.target)) continue;
    steps.push({
      id: def.id,
      target: variant.target,
      titleKey: variant.titleKey,
      bodyKey: def.bodyKey,
      placement: variant.placement,
    });
  }
  if (steps.length === 0) return [];
  return [{ id: WELCOME_STEP.id, target: null, titleKey: WELCOME_STEP.titleKey, bodyKey: WELCOME_STEP.bodyKey }, ...steps];
}

/** Phần tử `data-tour` đầu tiên đang thực sự hiển thị (header mobile/desktop cùng render, ẩn bằng CSS). */
export function findTourTarget(target: string, root: ParentNode = document): HTMLElement | null {
  const nodes = root.querySelectorAll<HTMLElement>(`[data-tour="${target}"]`);
  for (const node of nodes) {
    if (node.getClientRects().length > 0) return node;
  }
  return null;
}
