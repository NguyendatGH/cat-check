import { lazy } from "react";

/**
 * React.lazy cho từng page P1 (code-splitting theo route). Route Phase 2/3 dùng chung
 * ComingSoonPage (import thường, không cần lazy vì rất nhẹ) — xem router.tsx.
 */
export const LazyPages = {
  home: lazy(() => import("@/pages/public/HomePage").then((m) => ({ default: m.HomePage }))),
  login: lazy(() => import("@/pages/auth/LoginPage").then((m) => ({ default: m.LoginPage }))),
  register: lazy(() => import("@/pages/auth/RegisterPage").then((m) => ({ default: m.RegisterPage }))),
  verifyOtp: lazy(() => import("@/pages/auth/VerifyOtpPage").then((m) => ({ default: m.VerifyOtpPage }))),
  forgotPassword: lazy(() =>
    import("@/pages/auth/ForgotPasswordPage").then((m) => ({ default: m.ForgotPasswordPage })),
  ),
  resetPassword: lazy(() => import("@/pages/auth/ResetPasswordPage").then((m) => ({ default: m.ResetPasswordPage }))),
  oAuthComplete: lazy(() => import("@/pages/auth/OAuthCompletePage").then((m) => ({ default: m.OAuthCompletePage }))),
  terms: lazy(() => import("@/pages/legal/TermsPage").then((m) => ({ default: m.TermsPage }))),
  privacy: lazy(() => import("@/pages/legal/PrivacyPage").then((m) => ({ default: m.PrivacyPage }))),
  medicalDisclaimer: lazy(() =>
    import("@/pages/legal/MedicalDisclaimerPage").then((m) => ({ default: m.MedicalDisclaimerPage })),
  ),
  cookies: lazy(() => import("@/pages/legal/CookiesPage").then((m) => ({ default: m.CookiesPage }))),
  dataRequests: lazy(() => import("@/pages/legal/DataRequestsPage").then((m) => ({ default: m.DataRequestsPage }))),
  contact: lazy(() => import("@/pages/legal/ContactPage").then((m) => ({ default: m.ContactPage }))),
  complaints: lazy(() => import("@/pages/legal/ComplaintsPage").then((m) => ({ default: m.ComplaintsPage }))),
  policyVersions: lazy(() =>
    import("@/pages/legal/PolicyVersionsPage").then((m) => ({ default: m.PolicyVersionsPage })),
  ),
  policyVersionDetail: lazy(() =>
    import("@/pages/legal/PolicyVersionDetailPage").then((m) => ({ default: m.PolicyVersionDetailPage })),
  ),
  onboardingCat: lazy(() =>
    import("@/pages/onboarding/OnboardingCatPage").then((m) => ({ default: m.OnboardingCatPage })),
  ),
  onboardingHealthSurvey: lazy(() =>
    import("@/pages/onboarding/OnboardingHealthSurveyPage").then((m) => ({ default: m.OnboardingHealthSurveyPage })),
  ),
  onboardingDisclaimer: lazy(() =>
    import("@/pages/onboarding/OnboardingDisclaimerPage").then((m) => ({ default: m.OnboardingDisclaimerPage })),
  ),
  onboardingActivate: lazy(() =>
    import("@/pages/onboarding/OnboardingActivatePage").then((m) => ({ default: m.OnboardingActivatePage })),
  ),
  onboardingSuccess: lazy(() =>
    import("@/pages/onboarding/OnboardingSuccessPage").then((m) => ({ default: m.OnboardingSuccessPage })),
  ),
  dashboard: lazy(() => import("@/pages/dashboard/DashboardPage").then((m) => ({ default: m.DashboardPage }))),
  assistant: lazy(() => import("@/pages/assistant/AssistantPage").then((m) => ({ default: m.AssistantPage }))),
  selectCat: lazy(() => import("@/pages/scan/SelectCatPage").then((m) => ({ default: m.SelectCatPage }))),
  scan: lazy(() => import("@/pages/scan/ScanPage").then((m) => ({ default: m.ScanPage }))),
  scanResult: lazy(() => import("@/pages/scan/ScanResultPage").then((m) => ({ default: m.ScanResultPage }))),
  scanReassignCat: lazy(() =>
    import("@/pages/scan/ScanReassignCatPage").then((m) => ({ default: m.ScanReassignCatPage })),
  ),
  catsList: lazy(() => import("@/pages/cat/CatsListPage").then((m) => ({ default: m.CatsListPage }))),
  catNew: lazy(() => import("@/pages/cat/CatNewPage").then((m) => ({ default: m.CatNewPage }))),
  catDetail: lazy(() => import("@/pages/cat/CatDetailPage").then((m) => ({ default: m.CatDetailPage }))),
  catEdit: lazy(() => import("@/pages/cat/CatEditPage").then((m) => ({ default: m.CatEditPage }))),
  catHistory: lazy(() => import("@/pages/history/CatHistoryPage").then((m) => ({ default: m.CatHistoryPage }))),
  catTrends: lazy(() => import("@/pages/trends/CatTrendsPage").then((m) => ({ default: m.CatTrendsPage }))),
  scanDetail: lazy(() => import("@/pages/scan/ScanDetailPage").then((m) => ({ default: m.ScanDetailPage }))),
  sharedTrayLog: lazy(() =>
    import("@/pages/dashboard/SharedTrayLogPage").then((m) => ({ default: m.SharedTrayLogPage })),
  ),
  historyRedirect: lazy(() =>
    import("@/pages/history/HistoryRedirectPage").then((m) => ({ default: m.HistoryRedirectPage })),
  ),
  remindersList: lazy(() =>
    import("@/pages/reminder/RemindersListPage").then((m) => ({ default: m.RemindersListPage })),
  ),
  reminderNew: lazy(() => import("@/pages/reminder/ReminderNewPage").then((m) => ({ default: m.ReminderNewPage }))),
  reminderDetail: lazy(() =>
    import("@/pages/reminder/ReminderDetailPage").then((m) => ({ default: m.ReminderDetailPage })),
  ),
  export: lazy(() => import("@/pages/export/ExportPage").then((m) => ({ default: m.ExportPage }))),
  exportJob: lazy(() => import("@/pages/export/ExportJobPage").then((m) => ({ default: m.ExportJobPage }))),
  credits: lazy(() => import("@/pages/credit/CreditsPage").then((m) => ({ default: m.CreditsPage }))),
  creditsActivate: lazy(() =>
    import("@/pages/credit/CreditsActivatePage").then((m) => ({ default: m.CreditsActivatePage })),
  ),
  notifications: lazy(() =>
    import("@/pages/notification/NotificationsPage").then((m) => ({ default: m.NotificationsPage })),
  ),
  settings: lazy(() => import("@/pages/settings/SettingsPage").then((m) => ({ default: m.SettingsPage }))),
  settingsProfile: lazy(() =>
    import("@/pages/settings/SettingsProfilePage").then((m) => ({ default: m.SettingsProfilePage })),
  ),
  settingsSecurity: lazy(() =>
    import("@/pages/settings/SettingsSecurityPage").then((m) => ({ default: m.SettingsSecurityPage })),
  ),
  settingsNotifications: lazy(() =>
    import("@/pages/settings/SettingsNotificationsPage").then((m) => ({ default: m.SettingsNotificationsPage })),
  ),
  settingsLanguage: lazy(() =>
    import("@/pages/settings/SettingsLanguagePage").then((m) => ({ default: m.SettingsLanguagePage })),
  ),
  accountPrivacy: lazy(() =>
    import("@/pages/dashboard/AccountPrivacyPage").then((m) => ({ default: m.AccountPrivacyPage })),
  ),
  install: lazy(() => import("@/pages/dashboard/InstallPage").then((m) => ({ default: m.InstallPage }))),
  adminDashboard: lazy(() =>
    import("@/pages/admin/AdminDashboardPage").then((m) => ({ default: m.AdminDashboardPage })),
  ),
  adminUsers: lazy(() => import("@/pages/admin/AdminUsersPage").then((m) => ({ default: m.AdminUsersPage }))),
  adminUserDetail: lazy(() =>
    import("@/pages/admin/AdminUserDetailPage").then((m) => ({ default: m.AdminUserDetailPage })),
  ),
  adminActivationCodes: lazy(() =>
    import("@/pages/admin/AdminActivationCodesPage").then((m) => ({ default: m.AdminActivationCodesPage })),
  ),
  adminProducts: lazy(() => import("@/pages/admin/AdminProductsPage").then((m) => ({ default: m.AdminProductsPage }))),
  adminPackages: lazy(() => import("@/pages/admin/AdminPackagesPage").then((m) => ({ default: m.AdminPackagesPage }))),
  adminPhColorChart: lazy(() =>
    import("@/pages/admin/AdminPhColorChartPage").then((m) => ({ default: m.AdminPhColorChartPage })),
  ),
  adminContent: lazy(() => import("@/pages/admin/AdminContentPage").then((m) => ({ default: m.AdminContentPage }))),
  adminCommunityReports: lazy(() =>
    import("@/pages/admin/AdminCommunityReportsPage").then((m) => ({ default: m.AdminCommunityReportsPage })),
  ),
  adminPrivacyRequests: lazy(() =>
    import("@/pages/admin/AdminPrivacyRequestsPage").then((m) => ({ default: m.AdminPrivacyRequestsPage })),
  ),
  adminPrivacyRetention: lazy(() =>
    import("@/pages/admin/AdminPrivacyRetentionPage").then((m) => ({ default: m.AdminPrivacyRetentionPage })),
  ),
  adminSetup2fa: lazy(() => import("@/pages/admin/AdminSetup2faPage").then((m) => ({ default: m.AdminSetup2faPage }))),
  adminMeSecurity: lazy(() =>
    import("@/pages/admin/AdminMeSecurityPage").then((m) => ({ default: m.AdminMeSecurityPage })),
  ),
  adminStaff: lazy(() => import("@/pages/admin/AdminStaffPage").then((m) => ({ default: m.AdminStaffPage }))),
  adminBroadcast: lazy(() =>
    import("@/pages/admin/AdminBroadcastPage").then((m) => ({ default: m.AdminBroadcastPage })),
  ),
  adminJobs: lazy(() => import("@/pages/admin/AdminJobsPage").then((m) => ({ default: m.AdminJobsPage }))),
  adminNotificationsOutbox: lazy(() =>
    import("@/pages/admin/AdminNotificationsOutboxPage").then((m) => ({ default: m.AdminNotificationsOutboxPage })),
  ),
  adminAuditLog: lazy(() => import("@/pages/admin/AdminAuditLogPage").then((m) => ({ default: m.AdminAuditLogPage }))),
  offline: lazy(() => import("@/pages/system/OfflinePage").then((m) => ({ default: m.OfflinePage }))),
  notFound: lazy(() => import("@/pages/system/NotFoundPage").then((m) => ({ default: m.NotFoundPage }))),
  // Shop (Phase 3) — chỉ có UI + mock data, chưa có backend. Xem src/pages/shop/mockData.ts.
  shop: lazy(() => import("@/pages/shop/ShopPage").then((m) => ({ default: m.ShopPage }))),
  shopProductDetail: lazy(() =>
    import("@/pages/shop/ProductDetailPage").then((m) => ({ default: m.ProductDetailPage })),
  ),
  cart: lazy(() => import("@/pages/shop/CartPage").then((m) => ({ default: m.CartPage }))),
  checkout: lazy(() => import("@/pages/shop/CheckoutPage").then((m) => ({ default: m.CheckoutPage }))),
  orderTracking: lazy(() => import("@/pages/shop/OrderTrackingPage").then((m) => ({ default: m.OrderTrackingPage }))),
  // Cộng đồng (Phase 2 theo p4 — `post`/`comment` chưa đặc tả, chưa viết migration ở Phase 1).
  // Chỉ có UI + mock data. Xem src/pages/community/mockData.ts.
  communityFeed: lazy(() => import("@/pages/community").then((m) => ({ default: m.CommunityFeedPage }))),
  communityPostDetail: lazy(() => import("@/pages/community").then((m) => ({ default: m.CommunityPostDetailPage }))),
  communityNewPost: lazy(() => import("@/pages/community").then((m) => ({ default: m.CommunityNewPostPage }))),
  // Bản đồ & phòng khám (Phase 2 — `place`/`place_review` cùng lý do trên).
  catCareMap: lazy(() => import("@/pages/map").then((m) => ({ default: m.CatCareMapPage }))),
  clinicDetail: lazy(() => import("@/pages/map").then((m) => ({ default: m.ClinicDetailPage }))),
} as const;
