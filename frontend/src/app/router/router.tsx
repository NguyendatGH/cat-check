import { createBrowserRouter, useLocation, type RouteObject } from "react-router";
import { lazy, Suspense, useRef, type ReactNode } from "react";
import { DelayedFallback, PageSkeleton } from "@/shared/ui";
import { ComingSoonPage } from "@/pages/system/ComingSoonPage";
import { featureFlags, type FeatureFlagKey } from "@/shared/config/featureFlags";
import { PublicLayout } from "../layouts/PublicLayout";
import { AuthLayout } from "../layouts/AuthLayout";
import { OnboardingLayout } from "../layouts/OnboardingLayout";
import { AppLayout } from "../layouts/AppLayout";
import { TaskLayout } from "../layouts/TaskLayout";
import { FullscreenLayout } from "../layouts/FullscreenLayout";
import { SellerLayout } from "../layouts/SellerLayout";
import { useBreakpoint } from "@/shared/lib/hooks/useBreakpoint";
import { usePageReveal } from "@/shared/lib/motion/usePageReveal";
import { RequireAuth } from "./guards/RequireAuth";
import { RequireOnboarding } from "./guards/RequireOnboarding";
import { RequireRole } from "./guards/RequireRole";
import { RedirectIfAuthenticated } from "./guards/RedirectIfAuthenticated";
import { LazyPages } from "./lazy";
import { ROLES } from "@/shared/auth/roles";

const AdminLayout = lazy(() => import("../layouts/AdminLayout").then((module) => ({ default: module.AdminLayout })));

function PageEntrance({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  return <PageEntranceMotion key={pathname}>{children}</PageEntranceMotion>;
}

function PageEntranceMotion({ children }: { children: ReactNode }) {
  const pageRef = useRef<HTMLDivElement>(null);
  usePageReveal(pageRef);
  return (
    <div ref={pageRef} className="page-transition-root">
      {children}
    </div>
  );
}

function PageSuspense({ children }: { children: ReactNode }) {
  return (
    <Suspense
      fallback={
        <DelayedFallback fallback={<PageSkeleton />} delay={250}>
          <div />
        </DelayedFallback>
      }
    >
      <PageEntrance>{children}</PageEntrance>
    </Suspense>
  );
}

export function featureGated(flag: FeatureFlagKey, element: ReactNode): ReactNode {
  return featureFlags[flag] ? element : <ComingSoonPage />;
}

function ScanRouteLayout() {
  const isDesktop = useBreakpoint("md");
  return isDesktop ? <AppLayout /> : <FullscreenLayout />;
}

export const routes: RouteObject[] = [
  {
    element: <FullscreenLayout />,
    children: [
      {
        element: <RedirectIfAuthenticated />,
        children: [
          {
            index: true,
            element: (
              <PageSuspense>
                <LazyPages.home />
              </PageSuspense>
            ),
          },
        ],
      },
    ],
  },
  {
    element: <PublicLayout />,
    children: [
      {
        path: "/legal/terms",
        element: (
          <PageSuspense>
            <LazyPages.terms />
          </PageSuspense>
        ),
      },
      {
        path: "/legal/privacy",
        element: (
          <PageSuspense>
            <LazyPages.privacy />
          </PageSuspense>
        ),
      },
      {
        path: "/legal/medical-disclaimer",
        element: (
          <PageSuspense>
            <LazyPages.medicalDisclaimer />
          </PageSuspense>
        ),
      },
      {
        path: "/legal/cookies",
        element: (
          <PageSuspense>
            <LazyPages.cookies />
          </PageSuspense>
        ),
      },
      {
        path: "/legal/data-requests",
        element: (
          <PageSuspense>
            <LazyPages.dataRequests />
          </PageSuspense>
        ),
      },
      {
        path: "/legal/contact",
        element: (
          <PageSuspense>
            <LazyPages.contact />
          </PageSuspense>
        ),
      },
      {
        path: "/legal/complaints",
        element: (
          <PageSuspense>
            <LazyPages.complaints />
          </PageSuspense>
        ),
      },
      {
        path: "/legal/:policyCode/versions",
        element: (
          <PageSuspense>
            <LazyPages.policyVersions />
          </PageSuspense>
        ),
      },
      {
        path: "/legal/:policyCode/v/:version",
        element: (
          <PageSuspense>
            <LazyPages.policyVersionDetail />
          </PageSuspense>
        ),
      },
    ],
  },
  {
    element: <AuthLayout />,
    children: [
      {
        element: <RedirectIfAuthenticated />,
        children: [
          {
            path: "/auth/login",
            handle: { titleKey: "pages.login.title", hideTopBar: true },
            element: (
              <PageSuspense>
                <LazyPages.login />
              </PageSuspense>
            ),
          },
          {
            path: "/auth/register",
            handle: { titleKey: "pages.register.title", hideTopBar: true },
            element: (
              <PageSuspense>
                <LazyPages.register />
              </PageSuspense>
            ),
          },
          {
            path: "/auth/verify-otp",
            handle: { titleKey: "pages.verifyOtp.title" },
            element: (
              <PageSuspense>
                <LazyPages.verifyOtp />
              </PageSuspense>
            ),
          },
          {
            path: "/auth/forgot-password",
            handle: { titleKey: "pages.forgotPassword.title" },
            element: (
              <PageSuspense>
                <LazyPages.forgotPassword />
              </PageSuspense>
            ),
          },
          {
            path: "/auth/reset-password",
            handle: { titleKey: "pages.resetPassword.title" },
            element: (
              <PageSuspense>
                <LazyPages.resetPassword />
              </PageSuspense>
            ),
          },
          {
            path: "/auth/oauth/complete",
            handle: { titleKey: "pages.oauthComplete.title" },
            element: (
              <PageSuspense>
                <LazyPages.oAuthComplete />
              </PageSuspense>
            ),
          },
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
          {
            path: "/onboarding/cat",
            element: (
              <PageSuspense>
                <LazyPages.onboardingCat />
              </PageSuspense>
            ),
          },
          {
            path: "/onboarding/health-survey",
            element: (
              <PageSuspense>
                <LazyPages.onboardingHealthSurvey />
              </PageSuspense>
            ),
          },
          {
            path: "/onboarding/disclaimer",
            element: (
              <PageSuspense>
                <LazyPages.onboardingDisclaimer />
              </PageSuspense>
            ),
          },
          {
            path: "/onboarding/activate",
            element: (
              <PageSuspense>
                <LazyPages.onboardingActivate />
              </PageSuspense>
            ),
          },
          {
            path: "/onboarding/success",
            element: (
              <PageSuspense>
                <LazyPages.onboardingSuccess />
              </PageSuspense>
            ),
          },
        ],
      },
    ],
  },
  {
    element: <AppLayout />,
    children: [
      {
        path: "/dashboard",
        element: (
          <PageSuspense>
            <LazyPages.dashboard />
          </PageSuspense>
        ),
      },
      {
        path: "/shop",
        element: featureGated(
          "shop",
          <PageSuspense>
            <LazyPages.shop />
          </PageSuspense>,
        ),
      },
      {
        path: "/shop/products/:productId",
        element: featureGated(
          "shop",
          <PageSuspense>
            <LazyPages.shopProductDetail />
          </PageSuspense>,
        ),
      },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <TaskLayout />,
        children: [
          {
            path: "/cart",
            element: featureGated(
              "shop",
              <PageSuspense>
                <LazyPages.cart />
              </PageSuspense>,
            ),
          },
          {
            path: "/checkout",
            element: featureGated(
              "shop",
              <PageSuspense>
                <LazyPages.checkout />
              </PageSuspense>,
            ),
          },
          {
            path: "/orders/:orderId",
            element: featureGated(
              "shop",
              <PageSuspense>
                <LazyPages.orderTracking />
              </PageSuspense>,
            ),
          },
        ],
      },
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
              {
                path: "/assistant",
                element: (
                  <PageSuspense>
                    <LazyPages.assistant />
                  </PageSuspense>
                ),
              },
              {
                path: "/notifications",
                element: (
                  <PageSuspense>
                    <LazyPages.notifications />
                  </PageSuspense>
                ),
              },
              {
                path: "/cats",
                element: (
                  <PageSuspense>
                    <LazyPages.catsList />
                  </PageSuspense>
                ),
              },
              {
                path: "/cats/:catId",
                element: (
                  <PageSuspense>
                    <LazyPages.catDetail />
                  </PageSuspense>
                ),
              },
              {
                path: "/cats/:catId/history",
                element: (
                  <PageSuspense>
                    <LazyPages.catHistory />
                  </PageSuspense>
                ),
              },
              {
                path: "/cats/:catId/trends",
                element: (
                  <PageSuspense>
                    <LazyPages.catTrends />
                  </PageSuspense>
                ),
              },
              {
                path: "/scan/result/:scanId",
                element: (
                  <PageSuspense>
                    <LazyPages.scanResult />
                  </PageSuspense>
                ),
              },
              {
                path: "/scan/:scanId/reassign-cat",
                element: (
                  <PageSuspense>
                    <LazyPages.scanReassignCat />
                  </PageSuspense>
                ),
              },
              {
                path: "/shared-tray-log",
                element: (
                  <PageSuspense>
                    <LazyPages.sharedTrayLog />
                  </PageSuspense>
                ),
              },
              {
                path: "/history",
                element: (
                  <PageSuspense>
                    <LazyPages.historyRedirect />
                  </PageSuspense>
                ),
              },
              {
                path: "/credits",
                element: (
                  <PageSuspense>
                    <LazyPages.credits />
                  </PageSuspense>
                ),
              },
              {
                path: "/settings",
                element: (
                  <PageSuspense>
                    <LazyPages.settings />
                  </PageSuspense>
                ),
              },
              {
                path: "/community",
                element: featureGated(
                  "community",
                  <PageSuspense>
                    <LazyPages.communityFeed />
                  </PageSuspense>,
                ),
              },
              {
                path: "/community/posts/:postId",
                element: featureGated(
                  "community",
                  <PageSuspense>
                    <LazyPages.communityPostDetail />
                  </PageSuspense>,
                ),
              },
              {
                path: "/map",
                element: featureGated(
                  "map",
                  <PageSuspense>
                    <LazyPages.catCareMap />
                  </PageSuspense>,
                ),
              },
              {
                path: "/map/clinics/:clinicId",
                element: featureGated(
                  "map",
                  <PageSuspense>
                    <LazyPages.clinicDetail />
                  </PageSuspense>,
                ),
              },
            ],
          },
          {
            element: <TaskLayout />,
            children: [
              {
                path: "/scan/select-cat",
                element: (
                  <PageSuspense>
                    <LazyPages.selectCat />
                  </PageSuspense>
                ),
              },
              {
                path: "/cats/new",
                element: (
                  <PageSuspense>
                    <LazyPages.catNew />
                  </PageSuspense>
                ),
              },
              {
                path: "/cats/:catId/edit",
                element: (
                  <PageSuspense>
                    <LazyPages.catEdit />
                  </PageSuspense>
                ),
              },
              {
                path: "/scans/:scanId",
                element: (
                  <PageSuspense>
                    <LazyPages.scanDetail />
                  </PageSuspense>
                ),
              },
              {
                path: "/reminders",
                element: (
                  <PageSuspense>
                    <LazyPages.remindersList />
                  </PageSuspense>
                ),
              },
              {
                path: "/reminders/new",
                element: (
                  <PageSuspense>
                    <LazyPages.reminderNew />
                  </PageSuspense>
                ),
              },
              {
                path: "/reminders/:reminderId",
                element: (
                  <PageSuspense>
                    <LazyPages.reminderDetail />
                  </PageSuspense>
                ),
              },
              {
                path: "/export",
                element: (
                  <PageSuspense>
                    <LazyPages.export />
                  </PageSuspense>
                ),
              },
              {
                path: "/export/:jobId",
                element: (
                  <PageSuspense>
                    <LazyPages.exportJob />
                  </PageSuspense>
                ),
              },
              {
                path: "/credits/activate",
                element: (
                  <PageSuspense>
                    <LazyPages.creditsActivate />
                  </PageSuspense>
                ),
              },
              {
                path: "/settings/profile",
                element: (
                  <PageSuspense>
                    <LazyPages.settingsProfile />
                  </PageSuspense>
                ),
              },
              {
                path: "/settings/security",
                element: (
                  <PageSuspense>
                    <LazyPages.settingsSecurity />
                  </PageSuspense>
                ),
              },
              {
                path: "/settings/notifications",
                element: (
                  <PageSuspense>
                    <LazyPages.settingsNotifications />
                  </PageSuspense>
                ),
              },
              {
                path: "/settings/language",
                element: (
                  <PageSuspense>
                    <LazyPages.settingsLanguage />
                  </PageSuspense>
                ),
              },
              {
                path: "/account/privacy",
                element: (
                  <PageSuspense>
                    <LazyPages.accountPrivacy />
                  </PageSuspense>
                ),
              },
              {
                path: "/install",
                element: (
                  <PageSuspense>
                    <LazyPages.install />
                  </PageSuspense>
                ),
              },
              {
                path: "/community/new",
                element: featureGated(
                  "community",
                  <PageSuspense>
                    <LazyPages.communityNewPost />
                  </PageSuspense>,
                ),
              },
            ],
          },
          {
            element: <ScanRouteLayout />,
            children: [
              {
                path: "/scan",
                element: (
                  <PageSuspense>
                    <LazyPages.scan />
                  </PageSuspense>
                ),
              },
            ],
          },
        ],
      },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        path: "/admin/setup-2fa",
        element: (
          <PageSuspense>
            <LazyPages.adminSetup2fa />
          </PageSuspense>
        ),
      },
      {
        element: (
          <Suspense
            fallback={
              <DelayedFallback fallback={<PageSkeleton />} delay={400}>
                <div />
              </DelayedFallback>
            }
          >
            <AdminLayout />
          </Suspense>
        ),
        children: [
          {
            path: "/admin",
            element: (
              <RequireRole roles={ROLES.dashboard}>
                <PageSuspense>
                  <LazyPages.adminDashboard />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/users",
            element: (
              <RequireRole roles={ROLES.users}>
                <PageSuspense>
                  <LazyPages.adminUsers />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/users/:userId",
            element: (
              <RequireRole roles={ROLES.users}>
                <PageSuspense>
                  <LazyPages.adminUserDetail />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/activation-codes",
            element: (
              <RequireRole roles={ROLES.activationCodes}>
                <PageSuspense>
                  <LazyPages.adminActivationCodes />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/packages",
            element: (
              <RequireRole roles={ROLES.packagesRead}>
                <PageSuspense>
                  <LazyPages.adminPackages />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/products",
            element: (
              <RequireRole roles={ROLES.products}>
                <PageSuspense>
                  <LazyPages.adminProducts />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/ph-color-chart",
            element: (
              <RequireRole roles={ROLES.colorChart}>
                <PageSuspense>
                  <LazyPages.adminPhColorChart />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/content",
            element: (
              <RequireRole roles={ROLES.careTipsWrite}>
                <PageSuspense>
                  <LazyPages.adminContent />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/privacy/requests",
            element: (
              <RequireRole roles={ROLES.privacyRequests}>
                <PageSuspense>
                  <LazyPages.adminPrivacyRequests />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/privacy/retention",
            element: (
              <RequireRole roles={ROLES.retention}>
                <PageSuspense>
                  <LazyPages.adminPrivacyRetention />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/places",
            element: (
              <RequireRole roles={ROLES.adminPortal}>
                <PageEntrance>
                  <ComingSoonPage />
                </PageEntrance>
              </RequireRole>
            ),
          },
          {
            path: "/admin/community/reports",
            element: (
              <RequireRole roles={ROLES.communityModeration}>
                <PageSuspense>
                  <LazyPages.adminCommunityReports />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/me/security",
            element: (
              <RequireRole roles={ROLES.adminPortal}>
                <PageSuspense>
                  <LazyPages.adminMeSecurity />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/staff",
            element: (
              <RequireRole roles={ROLES.staff}>
                <PageSuspense>
                  <LazyPages.adminStaff />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/system/broadcast",
            element: (
              <RequireRole roles={ROLES.broadcast}>
                <PageSuspense>
                  <LazyPages.adminBroadcast />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/system/jobs",
            element: (
              <RequireRole roles={ROLES.jobs}>
                <PageSuspense>
                  <LazyPages.adminJobs />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/notifications/outbox",
            element: (
              <RequireRole roles={ROLES.outbox}>
                <PageSuspense>
                  <LazyPages.adminNotificationsOutbox />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/audit-log",
            element: (
              <RequireRole roles={ROLES.auditLog}>
                <PageSuspense>
                  <LazyPages.adminAuditLog />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/scans",
            element: (
              <RequireRole roles={ROLES.scans}>
                <PageSuspense>
                  <LazyPages.adminScans />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/monitoring-rules",
            element: (
              <RequireRole roles={ROLES.monitoringRulesRead}>
                <PageSuspense>
                  <LazyPages.adminMonitoringRules />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/security",
            element: (
              <RequireRole roles={ROLES.securityIncidentsRead}>
                <PageSuspense>
                  <LazyPages.adminSecurity />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/policy-versions",
            element: (
              <RequireRole roles={ROLES.policyVersionsRead}>
                <PageSuspense>
                  <LazyPages.adminPolicyVersions />
                </PageSuspense>
              </RequireRole>
            ),
          },
          {
            path: "/admin/privacy/consents",
            element: (
              <RequireRole roles={ROLES.consentsRead}>
                <PageSuspense>
                  <LazyPages.adminPrivacyConsents />
                </PageSuspense>
              </RequireRole>
            ),
          },
        ],
      },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: (
          <RequireRole roles={ROLES.seller}>
            <SellerLayout />
          </RequireRole>
        ),
        children: [
          {
            path: "/seller",
            element: (
              <PageSuspense>
                <LazyPages.sellerDashboard />
              </PageSuspense>
            ),
          },
          {
            path: "/seller/products",
            element: (
              <PageSuspense>
                <LazyPages.sellerProducts />
              </PageSuspense>
            ),
          },
          {
            path: "/seller/color-chart",
            element: (
              <PageSuspense>
                <LazyPages.adminPhColorChart />
              </PageSuspense>
            ),
          },
          {
            path: "/seller/content",
            element: (
              <PageSuspense>
                <LazyPages.adminContent />
              </PageSuspense>
            ),
          },
        ],
      },
    ],
  },
  {
    element: <FullscreenLayout />,
    children: [
      {
        path: "/offline",
        element: (
          <PageSuspense>
            <LazyPages.offline />
          </PageSuspense>
        ),
      },
      {
        path: "*",
        element: (
          <PageSuspense>
            <LazyPages.notFound />
          </PageSuspense>
        ),
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
