import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Plus } from "lucide-react";
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  SkeletonLoader,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/shared/ui";
import {
  CreditBalanceSummary,
  CreditBatchCard,
  EntitlementFeatureList,
  LedgerEntryRow,
  useCreditBalance,
  useCreditLedger,
  useEntitlement,
  usePackageCatalog,
} from "@/features/credit";

/**
 * `/credits` — p5 §5.8 (p9 route #41). Số dư, chi tiết từng lô, lịch sử, entitlement.
 *
 * DESKTOP (>= lg): `AppLayout` đã cấp hộp nội dung 944px + padding ngang, trang chỉ bỏ trần
 * `max-w-2xl` của mobile rồi chia theo đúng idiom đã chốt ở `settings` — cột nội dung co
 * giãn + cột phụ 360px. Tab "Tổng quan": số dư và các lô ở cột chính, quyền tính năng (khối
 * tham chiếu, ít thay đổi) ở cột phụ. Tab "Lịch sử giao dịch": dòng giao dịch ngắn nên xếp
 * lưới 2 cột thay vì kéo dài hết 944px.
 *
 * Mọi lệnh gọi API giữ nguyên — thay đổi thuần trình bày.
 */
export function CreditsPage() {
  const { t } = useTranslation(["credit", "common"]);
  const navigate = useNavigate();

  const balanceQuery = useCreditBalance();
  const ledgerQuery = useCreditLedger();
  const entitlementQuery = useEntitlement();
  // F3 — danh mục gói THẬT. Tên gói hiển thị ở mọi nơi trong trang lấy từ đây, không còn
  // bảng `credit.json: packages.*` cứng (xem `usePackageCatalog`).
  const packagesQuery = usePackageCatalog();
  const planName = (code: string | null): string =>
    (code ? packagesQuery.data?.find((plan) => plan.code === code)?.name : undefined) ?? code ?? "";

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6 lg:max-w-none">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.credits.title")}</h1>
        <Button
          type="button"
          size="sm"
          leftIcon={<Plus className="size-4" aria-hidden="true" />}
          onClick={() => {
            void navigate("/credits/activate");
          }}
        >
          {t("activateCta")}
        </Button>
      </div>

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">{t("tabs.overview")}</TabsTrigger>
          <TabsTrigger value="ledger">{t("tabs.ledger")}</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <div className="flex flex-col gap-6 pt-4 lg:flex-row lg:items-start lg:gap-6">
            <div className="flex min-w-0 flex-1 flex-col gap-6">
              {balanceQuery.isPending ? (
                <SkeletonLoader shape="card" />
              ) : balanceQuery.isError ? (
                <ErrorState
                  title={t("balance.errorTitle")}
                  onRetry={() => {
                    void balanceQuery.refetch();
                  }}
                  retryLabel={t("actions.retry", { ns: "common" })}
                />
              ) : (
                <>
                  <CreditBalanceSummary
                    availableBalance={balanceQuery.data.availableBalance}
                    trialScansRemaining={balanceQuery.data.trialScansRemaining}
                    nearestExpiringBatch={balanceQuery.data.batches[0]}
                  />

                  <div className="flex flex-col gap-2">
                    <h2 className="text-h3 font-semibold text-text-primary">{t("batch.sectionTitle")}</h2>
                    {balanceQuery.data.batches.length === 0 ? (
                      <EmptyState title={t("batch.emptyTitle")} description={t("batch.emptyDescription")} />
                    ) : (
                      <div className="flex flex-col gap-3">
                        {balanceQuery.data.batches.map((batch) => (
                          <CreditBatchCard key={batch.batchId} batch={batch} />
                        ))}
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>

            <div className="flex w-full flex-col gap-2 lg:w-[360px] lg:shrink-0">
              <h2 className="text-h3 font-semibold text-text-primary">{t("entitlement.sectionTitle")}</h2>
              <Card>
                {entitlementQuery.isPending ? (
                  <SkeletonLoader shape="text" />
                ) : entitlementQuery.isError ? (
                  <ErrorState
                    title={t("entitlement.errorTitle")}
                    onRetry={() => {
                      void entitlementQuery.refetch();
                    }}
                  />
                ) : (
                  <div className="flex flex-col gap-3">
                    <p className="text-caption text-text-secondary">
                      {entitlementQuery.data.currentPackage
                        ? t("entitlement.currentPackage", { name: planName(entitlementQuery.data.currentPackage) })
                        : t("entitlement.noPackage")}
                    </p>
                    <EntitlementFeatureList entitlement={entitlementQuery.data} />
                  </div>
                )}
              </Card>

              {/* Danh mục gói — F3 `GET /reference/packages`. KHÔNG có trường giá trong
                  response (thanh toán là Phase 3) nên trang không hiển thị giá. */}
              <h2 className="pt-2 text-h3 font-semibold text-text-primary">{t("catalog.sectionTitle")}</h2>
              <Card>
                {packagesQuery.isPending ? (
                  <SkeletonLoader shape="text" />
                ) : packagesQuery.isError ? (
                  <ErrorState
                    title={t("catalog.errorTitle")}
                    onRetry={() => {
                      void packagesQuery.refetch();
                    }}
                    retryLabel={t("actions.retry", { ns: "common" })}
                  />
                ) : packagesQuery.data.length === 0 ? (
                  <EmptyState title={t("catalog.emptyTitle")} description={t("catalog.emptyDescription")} />
                ) : (
                  <ul className="flex flex-col divide-y divide-border">
                    {packagesQuery.data.map((plan) => (
                      <li key={plan.code} className="flex flex-col gap-0.5 py-2.5 first:pt-0 last:pb-0">
                        <span className="text-caption font-semibold text-text-primary">{plan.name}</span>
                        <span className="text-caption text-text-secondary">
                          {t("catalog.credits", { count: plan.creditAmount })}
                          {" · "}
                          {t("catalog.validity", { days: plan.creditValidityDays })}
                          {" · "}
                          {t("catalog.weight", { value: plan.weightKg })}
                        </span>
                        <span className="text-small text-text-tertiary">
                          {plan.maxCatProfiles === null
                            ? t("catalog.catsUnlimited")
                            : t("catalog.catsLimit", { count: plan.maxCatProfiles })}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </div>
          </div>
        </TabsContent>

        <TabsContent value="ledger">
          <div className="flex flex-col gap-3 pt-4">
            {ledgerQuery.isPending ? (
              <SkeletonLoader shape="card" />
            ) : ledgerQuery.isError ? (
              <ErrorState
                title={t("ledger.errorTitle")}
                onRetry={() => {
                  void ledgerQuery.refetch();
                }}
                retryLabel={t("actions.retry", { ns: "common" })}
              />
            ) : ledgerQuery.data.pages[0]?.entries.length === 0 ? (
              <EmptyState title={t("ledger.emptyTitle")} description={t("ledger.emptyDescription")} />
            ) : (
              <>
                <div className="flex flex-col gap-3 lg:grid lg:grid-cols-2">
                  {ledgerQuery.data.pages
                    .flatMap((page) => page.entries)
                    .map((entry) => (
                      <LedgerEntryRow key={entry.id} entry={entry} />
                    ))}
                </div>
                {ledgerQuery.hasNextPage ? (
                  <Button
                    type="button"
                    variant="tertiary"
                    className="lg:mx-auto lg:w-[360px]"
                    loading={ledgerQuery.isFetchingNextPage}
                    onClick={() => {
                      void ledgerQuery.fetchNextPage();
                    }}
                  >
                    {t("ledger.loadMore")}
                  </Button>
                ) : null}
              </>
            )}
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
