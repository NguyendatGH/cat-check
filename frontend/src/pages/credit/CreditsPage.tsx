import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Check, List as ListIcon, Package, Plus } from "lucide-react";
import {
  Badge,
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
    // `AppLayout` không cấp padding cho mobile (chỉ `lg:px-8 lg:py-6`) ⇒ trang tự chừa lề + khoảng
    // đầu trang ở mobile; trước đây tiêu đề dính sát thanh header.
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-5 px-4 pb-6 pt-5 lg:max-w-none lg:p-0">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.credits.title")}</h1>
        <Button
          type="button"
          size="sm"
          className="shrink-0"
          leftIcon={<Plus className="size-4" aria-hidden="true" />}
          onClick={() => {
            void navigate("/credits/activate");
          }}
        >
          {t("activateCta")}
        </Button>
      </div>

      <Tabs defaultValue="overview">
        {/* Segmented 2 ô bằng nhau trải hết bề ngang — `x1_segmented` của M-16 / Web-17. */}
        <TabsList className="flex w-full">
          <TabsTrigger value="overview" className="flex-1">
            {t("tabs.overview")}
          </TabsTrigger>
          <TabsTrigger value="ledger" className="flex-1">
            {t("tabs.ledger")}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <div className="flex flex-col gap-6">
            {/*
              Web-17: hai cột — trái co giãn (số dư + chi tiết từng lô), phải 360px cố định
              (quyền tính năng). Bản trước xếp số dư cạnh quyền tính năng rồi đẩy các lô xuống
              một hàng riêng, để lại một thẻ lô đơn độc nửa bề ngang. Mobile (M-16): số dư → lô →
              quyền tính năng, đúng thứ tự frame.
            */}
            <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
              <div className="flex min-w-0 flex-1 flex-col gap-6">
                {balanceQuery.isPending ? (
                  <SkeletonLoader shape="card" className="h-40" />
                ) : balanceQuery.isError ? (
                  <ErrorState
                    title={t("balance.errorTitle")}
                    onRetry={() => {
                      void balanceQuery.refetch();
                    }}
                    retryLabel={t("actions.retry", { ns: "common" })}
                  />
                ) : (
                  <CreditBalanceSummary
                    availableBalance={balanceQuery.data.availableBalance}
                    trialScansRemaining={balanceQuery.data.trialScansRemaining}
                    nearestExpiringBatch={balanceQuery.data.batches[0]}
                  />
                )}

                {!balanceQuery.isPending && !balanceQuery.isError ? (
                  <section className="flex flex-col gap-3">
                    <h2 className="text-h3 font-semibold text-text-primary">{t("batch.sectionTitle")}</h2>
                    {balanceQuery.data.batches.length === 0 ? (
                      <Card className="p-0">
                        <EmptyState
                          icon={<Package className="size-6" aria-hidden="true" />}
                          title={t("batch.emptyTitle")}
                          description={t("batch.emptyDescription")}
                          action={
                            <Button
                              type="button"
                              size="sm"
                              onClick={() => {
                                void navigate("/credits/activate");
                              }}
                            >
                              {t("activateCta")}
                            </Button>
                          }
                        />
                      </Card>
                    ) : (
                      <div className="flex flex-col gap-3">
                        {balanceQuery.data.batches.map((batch) => (
                          <CreditBatchCard key={batch.batchId} batch={batch} />
                        ))}
                      </div>
                    )}
                  </section>
                ) : null}
              </div>

              <section className="flex w-full flex-col gap-3 lg:w-[360px] lg:shrink-0">
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
                      retryLabel={t("actions.retry", { ns: "common" })}
                    />
                  ) : (
                    <div className="flex flex-col gap-3">
                      <p className="text-caption text-text-secondary">
                        {entitlementQuery.data.currentPackage
                          ? t("entitlement.currentPackage", { name: planName(entitlementQuery.data.currentPackage) })
                          : t("entitlement.noPackage")}
                      </p>
                      <EntitlementFeatureList entitlement={entitlementQuery.data} />
                      {entitlementQuery.data.currentPackage ? (
                        <p className="border-t border-border pt-3 text-caption text-text-secondary">
                          {typeof entitlementQuery.data.maxCatProfiles === "number"
                            ? t("catalog.catsLimit", { count: entitlementQuery.data.maxCatProfiles })
                            : t("catalog.catsUnlimited")}
                        </p>
                      ) : null}
                    </div>
                  )}
                </Card>
              </section>
            </div>

            {/* Danh mục gói — F3 `GET /reference/packages`. KHÔNG có trường giá trong
                response (thanh toán là Phase 3) nên trang không hiển thị giá. */}
            <section className="flex flex-col gap-3">
              <h2 className="text-h3 font-semibold text-text-primary">{t("catalog.sectionTitle")}</h2>
              {packagesQuery.isPending ? (
                <SkeletonLoader shape="card" />
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
                <ul className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {packagesQuery.data.map((plan) => {
                    const features = [
                      plan.historyLevel !== "NONE" ? t("entitlement.features.history") : null,
                      plan.hasTrend ? t("entitlement.features.trend") : null,
                      plan.hasReminder ? t("entitlement.features.reminder") : null,
                      plan.hasExport ? t("entitlement.features.export") : null,
                      plan.storeImage ? t("entitlement.features.storeImage") : null,
                    ].filter((label): label is string => label !== null);
                    const isCurrent = entitlementQuery.data?.currentPackage === plan.code;
                    return (
                      <li
                        key={plan.code}
                        className="flex flex-col gap-2 rounded-2xl border border-border bg-surface p-4"
                      >
                        <div className="flex items-start justify-between gap-2">
                          <span className="text-body font-semibold text-text-primary">{plan.name}</span>
                          {isCurrent ? <Badge tone="brand">{t("catalog.currentBadge")}</Badge> : null}
                        </div>
                        <span className="text-caption text-text-secondary">
                          {t("catalog.credits", { count: plan.creditAmount })}
                          {" · "}
                          {t("catalog.validity", { days: plan.creditValidityDays })}
                          {" · "}
                          {t("catalog.weight", { value: plan.weightKg })}
                        </span>
                        <span className="text-small text-text-tertiary">
                          {typeof plan.maxCatProfiles === "number"
                            ? t("catalog.catsLimit", { count: plan.maxCatProfiles })
                            : t("catalog.catsUnlimited")}
                        </span>
                        {features.length > 0 ? (
                          <ul className="flex flex-wrap gap-1.5 pt-1">
                            {features.map((label) => (
                              <li
                                key={label}
                                className="inline-flex items-center gap-1 rounded-full bg-background-alt px-2 py-0.5 text-small text-text-secondary"
                              >
                                <Check className="size-3 shrink-0 text-success-text" aria-hidden="true" />
                                {label}
                              </li>
                            ))}
                          </ul>
                        ) : null}
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>
          </div>
        </TabsContent>

        <TabsContent value="ledger">
          <div className="flex flex-col gap-3">
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
              <EmptyState
                icon={<ListIcon className="size-6" aria-hidden="true" />}
                title={t("ledger.emptyTitle")}
                description={t("ledger.emptyDescription")}
              />
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
