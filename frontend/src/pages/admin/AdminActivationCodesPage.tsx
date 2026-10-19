import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Check, Copy, Download, Plus, ShieldOff } from "lucide-react";
import { Button, Dialog, DialogContent, DialogDescription, DialogTitle, Input, toast } from "@/shared/ui";
import {
  ACTIVATION_CODE_STATUSES,
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DensePanel,
  DenseTableFrame,
  EMPTY_CELL,
  KpiGrid,
  KpiTile,
  MutationErrorNote,
  Pager,
  ReasonConfirmDialog,
  ReasonField,
  TableSkeleton,
  activationEffectiveStatus,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  formatAdminNumber,
  isReasonValid,
  useAdminActivationBatches,
  useAdminActivationCodes,
  useAdminActivationCodeSummary,
  useAdminPackagePlans,
  useDownloadActivationBatchCsv,
  useIssueActivationBatch,
  useVoidActivationBatch,
  useVoidActivationCode,
} from "@/features/admin";
import type {
  ActivationBatch,
  ActivationCodeAdmin,
  ActivationCodeEffectiveStatus,
  IssuedActivationBatch,
} from "@/features/admin";
import { useGroup1Can } from "./group1Access";

const PAGE_SIZE = 20;
const DAY_MS = 86_400_000;

const STATUS_TONE: Record<ActivationCodeEffectiveStatus, "neutral" | "success" | "warning"> = {
  ISSUED: "neutral",
  EXPIRED: "warning",
  REDEEMED: "success",
  VOID: "warning",
};

/** Chọn gói từ danh sách gói đang bán; chỉ gọi API gói khi form sinh lô đang mở. */
function IssuePackageSelect({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { t } = useTranslation("admin");
  const plans = useAdminPackagePlans(false);
  const options = (plans.data ?? []).filter((plan) => plan.active);
  if (plans.isError || (plans.data && options.length === 0)) {
    return (
      <Input
        label={t("activation.packageCode")}
        value={value}
        className="h-10"
        onChange={(event) => {
          onChange(event.target.value);
        }}
        required
      />
    );
  }
  return (
    <AdminSelect
      label={t("activation.packageCode")}
      value={value}
      className="h-10"
      onChange={(event) => {
        onChange(event.target.value);
      }}
      required
    >
      <option value="">{t("activation.choosePackage")}</option>
      {options.map((plan) => (
        <option key={plan.code} value={plan.code}>
          {t("activation.packageOption", { code: plan.code, name: plan.name, credits: plan.creditAmount })}
        </option>
      ))}
    </AdminSelect>
  );
}

export function AdminActivationCodesPage() {
  const { t } = useTranslation("admin");
  const can = useGroup1Can();
  const canManage = can("activationCodesWrite");
  const [prefix, setPrefix] = useState("");
  const [packageCode, setPackageCode] = useState("");
  const [status, setStatus] = useState<ActivationCodeEffectiveStatus | "">("");
  const [batchId, setBatchId] = useState("");
  const [page, setPage] = useState(0);
  const [batchPage, setBatchPage] = useState(0);
  // Lý do hỏi tại thời điểm hành động (p17 A3): form sinh lô có ô lý do riêng, còn vô hiệu/tải CSV hỏi trong hộp xác nhận.
  const [issueReason, setIssueReason] = useState("");
  const [issuePackageCode, setIssuePackageCode] = useState("");
  const [pendingAction, setPendingAction] = useState<
    | { kind: "voidCode"; code: ActivationCodeAdmin }
    | { kind: "voidBatch"; batch: ActivationBatch }
    | { kind: "csv"; batch: ActivationBatch }
    | null
  >(null);
  const [showIssueForm, setShowIssueForm] = useState(false);
  // Raw codes exist only in the one-time CSV; keep them on screen after download so admins can copy them.
  const [revealedCodes, setRevealedCodes] = useState<{ batch: string; codes: string[] } | null>(null);
  const [justIssued, setJustIssued] = useState<IssuedActivationBatch | null>(null);
  const [quantity, setQuantity] = useState("100");
  const [validForDays, setValidForDays] = useState("365");
  const [productionBatch, setProductionBatch] = useState("");

  const codes = useAdminActivationCodes({ prefix, packageCode, status, batchId, page, size: PAGE_SIZE });
  const codeSummary = useAdminActivationCodeSummary({ prefix, packageCode, batchId });
  const batches = useAdminActivationBatches(batchPage, PAGE_SIZE);
  const issue = useIssueActivationBatch();
  const voidCode = useVoidActivationCode();
  const voidBatch = useVoidActivationBatch();
  const downloadCsv = useDownloadActivationBatchCsv();
  const canIssue = canManage && isReasonValid(issueReason);
  const activeMutation =
    pendingAction?.kind === "voidCode" ? voidCode : pendingAction?.kind === "voidBatch" ? voidBatch : downloadCsv;
  const codeItems = codes.data?.items ?? [];
  const validDays = Number(validForDays);
  const validUntilPreview =
    Number.isInteger(validDays) && validDays >= 1 && validDays <= 3650
      ? formatAdminDateTime(new Date(Date.now() + validDays * DAY_MS).toISOString())
      : null;

  function downloadBlob(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="flex flex-col gap-4">
      <AdminPageHeader
        title={t("pages.activationCodes.title")}
        description={t("pages.activationCodes.description")}
        actions={
          canManage ? (
            <Button
              variant="primary"
              size="sm"
              leftIcon={<Plus size={15} />}
              aria-expanded={showIssueForm}
              onClick={() => {
                setShowIssueForm((value) => !value);
              }}
            >
              {t("activation.issueButton")}
            </Button>
          ) : undefined
        }
      />

      {canManage && showIssueForm ? (
        <DensePanel title={t("activation.issueTitle")} description={t("activation.issueDescription")}>
          <form
            className="flex flex-col gap-3"
            onSubmit={(event) => {
              event.preventDefault();
              if (!canIssue) return;
              issue.mutate(
                {
                  packageCode: issuePackageCode.trim().toUpperCase(),
                  quantity: Number(quantity),
                  productionBatch: productionBatch.trim(),
                  validForDays: Number(validForDays),
                  reason: issueReason.trim(),
                },
                {
                  onSuccess: (issued) => {
                    setJustIssued(issued);
                    setShowIssueForm(false);
                    setIssueReason("");
                    setBatchId(productionBatch.trim());
                    setPage(0);
                    setBatchPage(0);
                  },
                },
              );
            }}
          >
            <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
              <IssuePackageSelect value={issuePackageCode} onChange={setIssuePackageCode} />
              <Input
                label={t("activation.productionBatch")}
                value={productionBatch}
                className="h-10"
                onChange={(event) => {
                  setProductionBatch(event.target.value);
                }}
                required
              />
              <Input
                label={t("activation.quantity")}
                type="number"
                min={1}
                max={50000}
                value={quantity}
                className="h-10"
                onChange={(event) => {
                  setQuantity(event.target.value);
                }}
                required
              />
              <Input
                label={t("activation.validForDays")}
                type="number"
                min={1}
                max={3650}
                value={validForDays}
                className="h-10"
                onChange={(event) => {
                  setValidForDays(event.target.value);
                }}
                required
              />
            </div>
            {validUntilPreview ? (
              <p className="text-caption text-text-secondary">
                {t("activation.validUntilPreview", { date: validUntilPreview })}
              </p>
            ) : null}
            <p className="text-caption text-text-secondary">{t("activation.singleUseNote")}</p>
            <ReasonField
              value={issueReason}
              onChange={setIssueReason}
              showError={issue.isError || issueReason.length > 0}
              disabled={issue.isPending}
            />
            <MutationErrorNote error={issue.error} />
            <div className="flex gap-2">
              <Button
                type="submit"
                variant="primary"
                size="sm"
                loading={issue.isPending}
                disabled={!canIssue || !issuePackageCode.trim() || !productionBatch.trim()}
              >
                {t("activation.issueSubmit")}
              </Button>
              <Button
                type="button"
                variant="tertiary"
                size="sm"
                onClick={() => {
                  setShowIssueForm(false);
                }}
              >
                {t("actions.cancel")}
              </Button>
            </div>
          </form>
        </DensePanel>
      ) : null}

      <DensePanel title={t("activation.filtersTitle")}>
        <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
          <Input
            label={t("activation.prefix")}
            value={prefix}
            className="h-10"
            onChange={(event) => {
              setPrefix(event.target.value);
              setPage(0);
            }}
          />
          <Input
            label={t("activation.packageCode")}
            value={packageCode}
            className="h-10"
            onChange={(event) => {
              setPackageCode(event.target.value);
              setPage(0);
            }}
          />
          <AdminSelect
            label={t("activation.statusLabel")}
            value={status}
            className="h-10"
            onChange={(event) => {
              setStatus(event.target.value as ActivationCodeEffectiveStatus | "");
              setPage(0);
            }}
          >
            <option value="">{t("activation.allStatuses")}</option>
            {ACTIVATION_CODE_STATUSES.map((value) => (
              <option key={value} value={value}>
                {t(`activation.status.${value}`)}
              </option>
            ))}
          </AdminSelect>
          <Input
            label={t("activation.batchId")}
            value={batchId}
            className="h-10"
            onChange={(event) => {
              setBatchId(event.target.value);
              setPage(0);
            }}
          />
        </div>
        {canManage ? null : <p className="text-caption text-text-secondary">{t("activation.readOnlyNote")}</p>}
      </DensePanel>

      {justIssued ? (
        <div
          role="status"
          className="flex flex-col gap-3 rounded-lg border border-warning/40 bg-warning-bg px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
        >
          <div className="min-w-0">
            <p className="text-body font-semibold text-text-primary">
              {t("activation.issuedNoticeTitle", { count: justIssued.quantity, batch: justIssued.productionBatch })}
            </p>
            <p className="text-caption text-text-secondary">{t("activation.issuedNoticeBody")}</p>
          </div>
          {justIssued.csvAvailable ? (
            <Button
              type="button"
              size="sm"
              leftIcon={<Download className="size-4" aria-hidden="true" />}
              onClick={() => {
                downloadCsv.reset();
                setPendingAction({
                  kind: "csv",
                  batch: {
                    batchId: justIssued.batchId,
                    productionBatch: justIssued.productionBatch,
                    packageCode: justIssued.packageCode,
                    totalCodes: justIssued.quantity,
                    issuedCodes: justIssued.quantity,
                    redeemedCodes: 0,
                    voidedCodes: 0,
                    issuedAt: justIssued.issuedAt,
                    validUntil: justIssued.validUntil,
                    csvAvailable: true,
                  },
                });
              }}
            >
              {t("activation.getCodesNow")}
            </Button>
          ) : null}
        </div>
      ) : null}

      {codes.data ? (
        <KpiGrid label={t("dense.kpiLabel")}>
          <KpiTile label={t("activation.kpiTotal")} value={formatAdminNumber(codes.data.totalElements)} tone="info" />
          {ACTIVATION_CODE_STATUSES.map((value) => (
            <KpiTile
              key={value}
              label={t(`activation.status.${value}`)}
              value={codeSummary.data ? formatAdminNumber(codeSummary.data.byStatus[value] ?? 0) : "—"}
              hint={t("dense.summaryIgnoresStatusHint")}
              tone={STATUS_TONE[value]}
            />
          ))}
        </KpiGrid>
      ) : null}

      <DensePanel title={t("activation.codesTitle")} description={t("activation.codesMaskedHint")}>
        {codes.isPending ? <TableSkeleton /> : null}
        {codes.isError ? (
          <AdminQueryError
            error={codes.error}
            onRetry={() => {
              void codes.refetch();
            }}
          />
        ) : null}
        {codes.data && codeItems.length === 0 ? (
          <p className="py-6 text-center text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {codes.data && codeItems.length > 0 ? (
          <>
            <DenseTableFrame label={t("activation.codesTitle")}>
              <table className={denseTableClass}>
                <thead>
                  <tr>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colPrefix")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colPackage")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colBatch")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colIssued")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colValidUntil")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colStatus")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colRedeemedAt")}
                    </th>
                    {canManage ? (
                      <th scope="col" className={denseThClass}>
                        {t("activation.colAction")}
                      </th>
                    ) : null}
                  </tr>
                </thead>
                <tbody>
                  {codeItems.map((code) => (
                    <tr key={code.id} className={denseRowClass}>
                      <td className={denseTdClass}>
                        <code className="font-mono text-small">{code.codePrefix}••••</code>
                      </td>
                      <td className={`${denseTdClass} font-mono text-small`}>{code.packageCode}</td>
                      <td className={denseTdClass}>{code.productionBatch}</td>
                      <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                        {formatAdminDateTime(code.issuedAt)}
                      </td>
                      <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                        {formatAdminDateTime(code.validUntil)}
                      </td>
                      <td className={`${denseTdClass} whitespace-nowrap`}>
                        {t(`activation.status.${activationEffectiveStatus(code)}`)}
                      </td>
                      <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                        {code.redeemedAt ? formatAdminDateTime(code.redeemedAt) : EMPTY_CELL}
                      </td>
                      {canManage ? (
                        <td className={denseTdClass}>
                          {code.status === "ISSUED" ? (
                            <Button
                              variant="tertiary"
                              size="sm"
                              leftIcon={<ShieldOff size={14} />}
                              disabled={voidCode.isPending}
                              onClick={() => {
                                voidCode.reset();
                                setPendingAction({ kind: "voidCode", code });
                              }}
                            >
                              {t("activation.void")}
                            </Button>
                          ) : (
                            EMPTY_CELL
                          )}
                        </td>
                      ) : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </DenseTableFrame>
            <Pager
              page={codes.data.number}
              totalPages={codes.data.totalPages}
              totalElements={codes.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
        {pendingAction === null ? <MutationErrorNote error={voidCode.error} /> : null}
      </DensePanel>

      <DensePanel title={t("activation.batchesTitle")}>
        {batches.isPending ? <TableSkeleton rows={4} /> : null}
        {batches.isError ? (
          <AdminQueryError
            error={batches.error}
            onRetry={() => {
              void batches.refetch();
            }}
          />
        ) : null}
        {batches.data && batches.data.items.length === 0 ? (
          <p className="py-6 text-center text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {batches.data && batches.data.items.length > 0 ? (
          <>
            <DenseTableFrame label={t("activation.batchesTitle")}>
              <table className={denseTableClass}>
                <thead>
                  <tr>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colBatch")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colPackage")}
                    </th>
                    <th scope="col" className={`${denseThClass} text-right`}>
                      {t("activation.colTotals")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colIssued")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("activation.colValidUntil")}
                    </th>
                    {canManage ? (
                      <th scope="col" className={denseThClass}>
                        {t("activation.colAction")}
                      </th>
                    ) : null}
                  </tr>
                </thead>
                <tbody>
                  {batches.data.items.map((batch) => (
                    <tr key={batch.batchId} className={denseRowClass}>
                      <td className={denseTdClass}>{batch.productionBatch}</td>
                      <td className={`${denseTdClass} font-mono text-small`}>{batch.packageCode}</td>
                      <td className={`${denseTdClass} whitespace-nowrap text-right font-mono tabular-nums`}>
                        {batch.totalCodes} / {batch.redeemedCodes} / {batch.voidedCodes}
                      </td>
                      <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                        {formatAdminDateTime(batch.issuedAt)}
                      </td>
                      <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                        {formatAdminDateTime(batch.validUntil)}
                      </td>
                      {canManage ? (
                        <td className={denseTdClass}>
                          <div className="flex flex-wrap gap-2">
                            {batch.csvAvailable ? (
                              <Button
                                variant="tertiary"
                                size="sm"
                                leftIcon={<Download size={14} />}
                                disabled={downloadCsv.isPending}
                                onClick={() => {
                                  downloadCsv.reset();
                                  setPendingAction({ kind: "csv", batch });
                                }}
                              >
                                {t("activation.downloadCsv")}
                              </Button>
                            ) : null}
                            <Button
                              variant="tertiary"
                              size="sm"
                              leftIcon={<ShieldOff size={14} />}
                              disabled={voidBatch.isPending || batch.issuedCodes <= 0}
                              onClick={() => {
                                voidBatch.reset();
                                setPendingAction({ kind: "voidBatch", batch });
                              }}
                            >
                              {t("activation.voidBatch")}
                            </Button>
                          </div>
                        </td>
                      ) : null}
                    </tr>
                  ))}
                </tbody>
              </table>
            </DenseTableFrame>
            <Pager
              page={batches.data.number}
              totalPages={batches.data.totalPages}
              totalElements={batches.data.totalElements}
              onPageChange={setBatchPage}
            />
          </>
        ) : null}
        {pendingAction === null ? <MutationErrorNote error={voidBatch.error ?? downloadCsv.error} /> : null}
      </DensePanel>
      <ReasonConfirmDialog
        open={pendingAction !== null}
        onOpenChange={(open) => {
          if (!open) setPendingAction(null);
        }}
        title={
          pendingAction?.kind === "voidCode"
            ? t("activation.void")
            : pendingAction?.kind === "voidBatch"
              ? t("activation.voidBatch")
              : t("activation.downloadCsv")
        }
        target={
          pendingAction === null
            ? ""
            : pendingAction.kind === "voidCode"
              ? `${pendingAction.code.codePrefix}•••• · ${pendingAction.code.packageCode}`
              : `${pendingAction.batch.productionBatch} · ${pendingAction.batch.packageCode}`
        }
        consequence={
          pendingAction?.kind === "voidCode"
            ? t("activation.voidConsequence")
            : pendingAction?.kind === "voidBatch"
              ? t("activation.voidBatchConsequence")
              : t("activation.csvConsequence")
        }
        confirmLabel={
          pendingAction?.kind === "voidCode"
            ? t("activation.void")
            : pendingAction?.kind === "voidBatch"
              ? t("activation.voidBatch")
              : t("activation.downloadCsv")
        }
        destructive={pendingAction?.kind !== "csv"}
        pending={activeMutation.isPending}
        error={activeMutation.error}
        onConfirm={(reason) => {
          if (pendingAction === null) return;
          const done = () => {
            setPendingAction(null);
          };
          if (pendingAction.kind === "voidCode") {
            voidCode.mutate({ codeId: pendingAction.code.id, reason }, { onSuccess: done });
          } else if (pendingAction.kind === "voidBatch") {
            voidBatch.mutate({ batchId: pendingAction.batch.batchId, reason }, { onSuccess: done });
          } else {
            const { batchId: target } = pendingAction.batch;
            downloadCsv.mutate(
              { batchId: target, reason },
              {
                onSuccess: (blob) => {
                  downloadBlob(blob, `activation-codes-${target}.csv`);
                  void blob.text().then((text) => {
                    setRevealedCodes({ batch: pendingAction.batch.productionBatch, codes: parseCodesCsv(text) });
                  });
                  if (justIssued?.batchId === target) setJustIssued(null);
                  done();
                },
              },
            );
          }
        }}
      />
      <RevealedCodesDialog
        value={revealedCodes}
        onClose={() => {
          setRevealedCodes(null);
        }}
      />
    </div>
  );
}

/** First column of the issuance CSV ("code,package_code,production_batch,valid_until"). */
function parseCodesCsv(text: string): string[] {
  return text
    .split(/\r?\n/)
    .slice(1)
    .map((line) => line.split(",")[0]?.trim() ?? "")
    .filter((code) => code.length > 0);
}

function RevealedCodesDialog({
  value,
  onClose,
}: {
  value: { batch: string; codes: string[] } | null;
  onClose: () => void;
}) {
  const { t } = useTranslation("admin");
  const [copied, setCopied] = useState<string | null>(null);

  const copy = async (text: string, key: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(key);
    } catch {
      toast.error(t("activation.copyFailed"));
    }
  };

  return (
    <Dialog
      open={value !== null}
      onOpenChange={(open) => {
        if (!open) {
          setCopied(null);
          onClose();
        }
      }}
    >
      <DialogContent aria-label={t("activation.revealedTitle")}>
        <DialogTitle>{t("activation.revealedTitle")}</DialogTitle>
        <DialogDescription>
          {t("activation.revealedBody", { batch: value?.batch ?? "", count: value?.codes.length ?? 0 })}
        </DialogDescription>
        <div className="mt-3 flex justify-end">
          <Button
            type="button"
            size="sm"
            variant="tertiary"
            leftIcon={copied === "all" ? <Check className="size-4" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}
            onClick={() => {
              void copy(value?.codes.join("\n") ?? "", "all");
            }}
          >
            {t("activation.copyAll")}
          </Button>
        </div>
        <ul className="mt-2 flex max-h-80 flex-col gap-1 overflow-y-auto">
          {value?.codes.map((code) => (
            <li key={code} className="flex items-center justify-between gap-2 rounded-md bg-background-alt px-3 py-1.5">
              <code className="font-mono text-small text-text-primary">{code}</code>
              <button
                type="button"
                aria-label={t("activation.copyCode", { code })}
                onClick={() => {
                  void copy(code, code);
                }}
                className="flex size-9 items-center justify-center rounded-md text-text-secondary hover:bg-surface hover:text-primary"
              >
                {copied === code ? <Check className="size-4" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}
              </button>
            </li>
          ))}
        </ul>
      </DialogContent>
    </Dialog>
  );
}
