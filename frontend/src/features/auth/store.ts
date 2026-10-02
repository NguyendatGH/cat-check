import { create } from "zustand";
import type { PendingRegistration } from "./types";

/**
 * Store zustand giữ state tạm giữa các bước của luồng auth (đăng ký -> verify-otp,
 * quên mật khẩu -> đặt lại mật khẩu, đăng nhập -> bước 2 MFA).
 *
 * KHÔNG persist (khác `onboardingDraft.store` ở p9 §9.5.2 vốn dùng `sessionStorage`):
 * `pendingRegistration` mang mật khẩu thô — không được nằm trong bất kỳ storage nào của
 * trình duyệt, kể cả sessionStorage (CLAUDE.md "Không log PII"; cùng tinh thần với quyết
 * định của A7 ở `features/onboarding/store.ts`). Mất state khi refresh giữa chừng là đánh
 * đổi chấp nhận được — người dùng bấm "Gửi lại mã" hoặc quay lại bước trước.
 */
interface AuthFlowStore {
  /** Nháp đăng ký chờ xác thực OTP — cần lại password để gọi `/auth/login` sau khi kích hoạt. */
  pendingRegistration: PendingRegistration | null;
  setPendingRegistration: (draft: PendingRegistration | null) => void;

  /** Email đang thực hiện quên mật khẩu — mang từ `/auth/forgot-password` sang bước nhập mã. */
  passwordResetEmail: string | null;
  setPasswordResetEmail: (email: string | null) => void;

  /** `otpTicket` sau khi verify mã PASSWORD_RESET — mang sang `/auth/reset-password`. */
  passwordResetTicket: string | null;
  setPasswordResetTicket: (ticket: string | null) => void;

  /** Phương thức MFA còn thiếu ở bước 2 đăng nhập (`mfaMethods` từ `POST /auth/login`). */
  pendingLoginMfaMethods: string[] | null;
  setPendingLoginMfaMethods: (methods: string[] | null) => void;

  reset: () => void;
}

export const useAuthFlowStore = create<AuthFlowStore>((set) => ({
  pendingRegistration: null,
  setPendingRegistration: (pendingRegistration) => {
    set({ pendingRegistration });
  },

  passwordResetEmail: null,
  setPasswordResetEmail: (passwordResetEmail) => {
    set({ passwordResetEmail });
  },

  passwordResetTicket: null,
  setPasswordResetTicket: (passwordResetTicket) => {
    set({ passwordResetTicket });
  },

  pendingLoginMfaMethods: null,
  setPendingLoginMfaMethods: (pendingLoginMfaMethods) => {
    set({ pendingLoginMfaMethods });
  },

  reset: () => {
    set({
      pendingRegistration: null,
      passwordResetEmail: null,
      passwordResetTicket: null,
      pendingLoginMfaMethods: null,
    });
  },
}));
