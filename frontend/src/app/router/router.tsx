import { createBrowserRouter } from "react-router";
import { Suspense, type ReactNode } from "react";
import { SkeletonLoader } from "@/shared/ui";
import { ComingSoonPage } from "@/pages/system/ComingSoonPage";
import { PublicLayout } from "../layouts/PublicLayout";
import { AuthLayout } from "../layouts/AuthLayout";
import { OnboardingLayout } from "../layouts/OnboardingLayout";
import { AppLayout } from "../layouts/AppLayout";
import { TaskLayout } from "../layouts/TaskLayout";
import { FullscreenLayout } from "../layouts/FullscreenLayout";
import { AdminLayout } from "../layouts/AdminLayout";
import { useBreakpoint } from "@/shared/lib/hooks/useBreakpoint";
import { RequireAuth } from "./guards/RequireAuth";
import { RequireOnboarding } from "./guards/RequireOnboarding";
import { RequireRole } from "./guards/RequireRole";
import { RedirectIfAuthenticated } from "./guards/RedirectIfAuthenticated";
import { LazyPages } from "./lazy";

/** Fallback Suspense cho mọi page lazy-load — SkeletonLoader dạng card. */
function PageSuspense({ children }: { children: ReactNode }) {
  return <Suspense fallback={<SkeletonLoader shape="card" className="m-4" />}>{children}</Suspense>;
}

/**
 * `/scan` là route responsive DUY NHẤT: FullscreenLayout trên mobile (chụp ảnh cần toàn
 * màn hình), AppLayout trên desktop (>= breakpoint md) — p9 §9.4.3 ghi "FL mobile/APL
 * desktop". Chọn layout bằng breakpoint thay vì đăng ký 2 route khác nhau.
 */
function ScanRouteLayout() {
  const isDesktop = useBreakpoint("md");
  return isDesktop ? <AppLayout /> : <FullscreenLayout />;
}

export const router = createBrowserRouter([
  // Màn Chào mừng (M1 màn 1) dùng FullscreenLayout chứ KHÔNG phải PublicLayout: thiết kế bắt
  // đầu thẳng bằng banner disclaimer, không có thanh header "CatCheck" mà PublicLayout vẽ.
  // Các trang pháp lý bên dưới vẫn giữ PublicLayout (chúng cần header để quay lại).
  {
    element: <FullscreenLayout />,
    children: [
      { element: <RedirectIfAuthenticated />, children: [{ index: true, element: <PageSuspense><LazyPages.home /></PageSuspense> }] },
    ],
  },
  {
    element: <PublicLayout />,
    children: [
      { path: "/legal/terms", element: <PageSuspense><LazyPages.terms /></PageSuspense> },
      { path: "/legal/privacy", element: <PageSuspense><LazyPages.privacy /></PageSuspense> },
      { path: "/legal/medical-disclaimer", element: <PageSuspense><LazyPages.medicalDisclaimer /></PageSuspense> },
      { path: "/legal/cookies", element: <PageSuspense><LazyPages.cookies /></PageSuspense> },
      { path: "/legal/data-requests", element: <PageSuspense><LazyPages.dataRequests /></PageSuspense> },
      { path: "/legal/contact", element: <PageSuspense><LazyPages.contact /></PageSuspense> },
      { path: "/legal/complaints", element: <PageSuspense><LazyPages.complaints /></PageSuspense> },
      { path: "/legal/:policyCode/versions", element: <PageSuspense><LazyPages.policyVersions /></PageSuspense> },
      { path: "/legal/:policyCode/v/:version", element: <PageSuspense><LazyPages.policyVersionDetail /></PageSuspense> },
    ],
  },
  {
    element: <AuthLayout />,
    children: [
      {
        element: <RedirectIfAuthenticated />,
        children: [
          { path: "/auth/login", handle: { titleKey: "pages.login.title" }, element: <PageSuspense><LazyPages.login /></PageSuspense> },
          { path: "/auth/register", handle: { titleKey: "pages.register.title" }, element: <PageSuspense><LazyPages.register /></PageSuspense> },
          { path: "/auth/verify-otp", handle: { titleKey: "pages.verifyOtp.title" }, element: <PageSuspense><LazyPages.verifyOtp /></PageSuspense> },
          { path: "/auth/forgot-password", handle: { titleKey: "pages.forgotPassword.title" }, element: <PageSuspense><LazyPages.forgotPassword /></PageSuspense> },
          { path: "/auth/reset-password", handle: { titleKey: "pages.resetPassword.title" }, element: <PageSuspense><LazyPages.resetPassword /></PageSuspense> },
          { path: "/auth/oauth/complete", handle: { titleKey: "pages.oauthComplete.title" }, element: <PageSuspense><LazyPages.oAuthComplete /></PageSuspense> },
        ],
      },
    ],
  },
  {
    element: <OnboardingLayout />,
    children: [
      {
        element: <RequireAuth />,
        children: [
          { path: "/onboarding/cat", element: <PageSuspense><LazyPages.onboardingCat /></PageSuspense> },
          { path: "/onboarding/health-survey", element: <PageSuspense><LazyPages.onboardingHealthSurvey /></PageSuspense> },
          { path: "/onboarding/disclaimer", element: <PageSuspense><LazyPages.onboardingDisclaimer /></PageSuspense> },
          { path: "/onboarding/activate", element: <PageSuspense><LazyPages.onboardingActivate /></PageSuspense> },
          { path: "/onboarding/success", element: <PageSuspense><LazyPages.onboardingSuccess /></PageSuspense> },
        ],
      },
    ],
  },
  // Trang chủ xem được khi CHƯA đăng nhập (chế độ khách): vẫn dùng AppLayout nhưng KHÔNG qua
  // RequireAuth/RequireOnboarding. `DashboardPage` tự đổi sang trạng thái khách — không gọi
  // API cần phiên, thay khối cá nhân hoá bằng CTA đăng nhập. Mọi route còn lại vẫn bị chặn.
  {
    element: <AppLayout />,
    children: [
      { path: "/dashboard", element: <PageSuspense><LazyPages.dashboard /></PageSuspense> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <RequireOnboarding />,
        children: [
          {
            element: <AppLayout />,
            children: [
              { path: "/cats", element: <PageSuspense><LazyPages.catsList /></PageSuspense> },
              { path: "/cats/:catId", element: <PageSuspense><LazyPages.catDetail /></PageSuspense> },
              { path: "/cats/:catId/history", element: <PageSuspense><LazyPages.catHistory /></PageSuspense> },
              { path: "/cats/:catId/trends", element: <PageSuspense><LazyPages.catTrends /></PageSuspense> },
              { path: "/scan/result/:scanId", element: <PageSuspense><LazyPages.scanResult /></PageSuspense> },
              { path: "/scan/:scanId/reassign-cat", element: <PageSuspense><LazyPages.scanReassignCat /></PageSuspense> },
              { path: "/shared-tray-log", element: <PageSuspense><LazyPages.sharedTrayLog /></PageSuspense> },
              { path: "/history", element: <PageSuspense><LazyPages.historyRedirect /></PageSuspense> },
              { path: "/credits", element: <PageSuspense><LazyPages.credits /></PageSuspense> },
              { path: "/settings", element: <PageSuspense><LazyPages.settings /></PageSuspense> },
              { path: "/community", element: <PageSuspense><LazyPages.communityFeed /></PageSuspense> },
              { path: "/community/posts/:postId", element: <PageSuspense><LazyPages.communityPostDetail /></PageSuspense> },
              { path: "/map", element: <PageSuspense><LazyPages.catCareMap /></PageSuspense> },
              { path: "/map/clinics/:clinicId", element: <PageSuspense><LazyPages.clinicDetail /></PageSuspense> },
              { path: "/shop", element: <PageSuspense><LazyPages.shop /></PageSuspense> },
              { path: "/shop/products/:productId", element: <PageSuspense><LazyPages.shopProductDetail /></PageSuspense> },
            ],
          },
          {
            element: <TaskLayout />,
            children: [
              { path: "/scan/select-cat", element: <PageSuspense><LazyPages.selectCat /></PageSuspense> },
              { path: "/cats/new", element: <PageSuspense><LazyPages.catNew /></PageSuspense> },
              { path: "/cats/:catId/edit", element: <PageSuspense><LazyPages.catEdit /></PageSuspense> },
              { path: "/scans/:scanId", element: <PageSuspense><LazyPages.scanDetail /></PageSuspense> },
              { path: "/reminders", element: <PageSuspense><LazyPages.remindersList /></PageSuspense> },
              { path: "/reminders/new", element: <PageSuspense><LazyPages.reminderNew /></PageSuspense> },
              { path: "/reminders/:reminderId", element: <PageSuspense><LazyPages.reminderDetail /></PageSuspense> },
              { path: "/export", element: <PageSuspense><LazyPages.export /></PageSuspense> },
              { path: "/export/:jobId", element: <PageSuspense><LazyPages.exportJob /></PageSuspense> },
              { path: "/credits/activate", element: <PageSuspense><LazyPages.creditsActivate /></PageSuspense> },
              { path: "/notifications", element: <PageSuspense><LazyPages.notifications /></PageSuspense> },
              { path: "/settings/profile", element: <PageSuspense><LazyPages.settingsProfile /></PageSuspense> },
              { path: "/settings/security", element: <PageSuspense><LazyPages.settingsSecurity /></PageSuspense> },
              { path: "/settings/notifications", element: <PageSuspense><LazyPages.settingsNotifications /></PageSuspense> },
              { path: "/settings/language", element: <PageSuspense><LazyPages.settingsLanguage /></PageSuspense> },
              { path: "/account/privacy", element: <PageSuspense><LazyPages.accountPrivacy /></PageSuspense> },
              { path: "/install", element: <PageSuspense><LazyPages.install /></PageSuspense> },
              { path: "/community/new", element: <PageSuspense><LazyPages.communityNewPost /></PageSuspense> },
              { path: "/cart", element: <PageSuspense><LazyPages.cart /></PageSuspense> },
              { path: "/checkout", element: <PageSuspense><LazyPages.checkout /></PageSuspense> },
              { path: "/orders/:orderId", element: <PageSuspense><LazyPages.orderTracking /></PageSuspense> },
            ],
          },
          {
            element: <ScanRouteLayout />,
            children: [{ path: "/scan", element: <PageSuspense><LazyPages.scan /></PageSuspense> }],
          },
        ],
      },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      { path: "/admin/setup-2fa", element: <PageSuspense><LazyPages.adminSetup2fa /></PageSuspense> },
      {
        element: <AdminLayout />,
        children: [
          { path: "/admin", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminDashboard /></PageSuspense></RequireRole> },
          { path: "/admin/users", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminUsers /></PageSuspense></RequireRole> },
          { path: "/admin/users/:userId", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminUserDetail /></PageSuspense></RequireRole> },
          { path: "/admin/activation-codes", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminActivationCodes /></PageSuspense></RequireRole> },
          { path: "/admin/packages", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminPackages /></PageSuspense></RequireRole> },
          { path: "/admin/ph-color-chart", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminPhColorChart /></PageSuspense></RequireRole> },
          { path: "/admin/content", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminContent /></PageSuspense></RequireRole> },
          { path: "/admin/privacy/requests", element: <RequireRole roles={["DPO"]}><PageSuspense><LazyPages.adminPrivacyRequests /></PageSuspense></RequireRole> },
          { path: "/admin/privacy/retention", element: <RequireRole roles={["DPO"]}><PageSuspense><LazyPages.adminPrivacyRetention /></PageSuspense></RequireRole> },
          { path: "/admin/places", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><ComingSoonPage /></RequireRole> },
          { path: "/admin/community/reports", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><ComingSoonPage /></RequireRole> },
          { path: "/admin/me/security", element: <RequireRole roles={["ADMIN", "ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminMeSecurity /></PageSuspense></RequireRole> },
          { path: "/admin/staff", element: <RequireRole roles={["ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminStaff /></PageSuspense></RequireRole> },
          { path: "/admin/system/broadcast", element: <RequireRole roles={["ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminBroadcast /></PageSuspense></RequireRole> },
          { path: "/admin/system/jobs", element: <RequireRole roles={["ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminJobs /></PageSuspense></RequireRole> },
          { path: "/admin/notifications/outbox", element: <RequireRole roles={["ADMIN_SUPER"]}><PageSuspense><LazyPages.adminNotificationsOutbox /></PageSuspense></RequireRole> },
          { path: "/admin/audit-log", element: <RequireRole roles={["ADMIN_SUPER", "DPO"]}><PageSuspense><LazyPages.adminAuditLog /></PageSuspense></RequireRole> },
        ],
      },
    ],
  },
  {
    element: <FullscreenLayout />,
    children: [
      { path: "/offline", element: <PageSuspense><LazyPages.offline /></PageSuspense> },
      { path: "*", element: <PageSuspense><LazyPages.notFound /></PageSuspense> },
    ],
  },
]);

