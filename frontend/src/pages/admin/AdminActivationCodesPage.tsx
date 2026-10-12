import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Download, Plus, ShieldOff } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import {
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
  ReasonField,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  formatAdminNumber,
  isReasonValid,
  useAdminActivationBatches,
  useAdminActivationCodes,
  useDownloadActivationBatchCsv,
  useIssueActivationBatch,
  useVoidActivationBatch,
  useVoidActivationCode,
} from "@/features/admin";
import type { ActivationCodeStatus } from "@/features/admin";
import { useGroup1Can } from "./group1Access";

const PAGE_SIZE = 20;
const CODE_STATUSES: ActivationCodeStatus[] = ["ISSUED", "REDEEMED", "VOID"];

export function AdminActivationCodesPage() {
  const { t } = useTranslation("admin");
  const can = useGroup1Can();
  const canManage = can("activationCodesWrite");
  const [prefix, setPrefix] = useState("");
  const [packageCode, setPackageCode] = useState("");
  const [status, setStatus] = useState<ActivationCodeStatus | "">("");
  const [batchId, setBatchId] = useState("");
  const [page, setPage] = useState(0);
  const [batchPage, setBatchPage] = useState(0);
  const [reason, setReason] = useState("");
  const [showIssueForm, setShowIssueForm] = useState(false);
  const [quantity, setQuantity] = useState("100");
  const [validForDays, setValidForDays] = useState("365");
  const [productionBatch, setProductionBatch] = useState("");

  const codes = useAdminActivationCodes({ prefix, packageCode, status, batchId, page, size: PAGE_SIZE });
  const batches = useAdminActivationBatches(batchPage, PAGE_SIZE);
  const issue = useIssueActivationBatch();
  const voidCode = useVoidActivationCode();
  const voidBatch = useVoidActivationBatch();
  const downloadCsv = useDownloadActivationBatchCsv();
  const canWrite = canManage && isReasonValid(reason);
  const codeItems = codes.data?.items ?? [];

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
        specRef="L19–L24 · GET/POST /api/v1/admin/activation-codes"
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
              if (!canWrite) return;
              issue.mutate(
                {
                  packageCode: packageCode.trim().toUpperCase(),
                  quantity: Number(quantity),
                  productionBatch: productionBatch.trim(),
                  validForDays: Number(validForDays),
                  reason: reason.trim(),
                },
                {
                  onSuccess: () => {
                    setShowIssueForm(false);
                    setBatchId(productionBatch.trim());
                    setBatchPage(0);
                  },
                },
              );
            }}
          >
            <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
              <Input
                label={t("activation.packageCode")}
                value={packageCode}
                className="h-10"
                onChange={(event) => {
                  setPackageCode(event.target.value);
                }}
                required
              />
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
            <ReasonField
              value={reason}
              onChange={setReason}
              showError={issue.isError || reason.length > 0}
              disabled={issue.isPending}
            />
            <MutationErrorNote error={issue.error} />
            <div className="flex gap-2">
              <Button
                type="submit"
                variant="primary"
                size="sm"
                loading={issue.isPending}
                disabled={!canWrite || !packageCode.trim() || !productionBatch.trim()}
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
              setStatus(event.target.value as ActivationCodeStatus | "");
              setPage(0);
            }}
          >
            <option value="">{t("activation.allStatuses")}</option>
            {CODE_STATUSES.map((value) => (
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
        {canManage ? (
          <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
        ) : (
          <p className="text-caption text-text-secondary">{t("activation.readOnlyNote")}</p>
        )}
      </DensePanel>

      {codes.data ? (
        <KpiGrid label={t("dense.kpiLabel")}>
          <KpiTile label={t("activation.kpiTotal")} value={formatAdminNumber(codes.data.totalElements)} tone="info" />
          {CODE_STATUSES.map((value) => (
            <KpiTile
              key={value}
              label={t(`activation.status.${value}`)}
              value={formatAdminNumber(codeItems.filter((code) => code.status === value).length)}
              hint={t("dense.onPageHint")}
              tone={value === "REDEEMED" ? "success" : value === "VOID" ? "warning" : "neutral"}
            />
          ))}
        </KpiGrid>
      ) : null}

      <DensePanel title={t("activation.codesTitle")}>
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
                      <td className={`${denseTdClass} whitespace-nowrap`}>{t(`activation.status.${code.status}`)}</td>
                      {canManage ? (
                        <td className={denseTdClass}>
                          {code.status === "ISSUED" ? (
                            <Button
                              variant="tertiary"
                              size="sm"
                              leftIcon={<ShieldOff size={14} />}
                              disabled={!canWrite || voidCode.isPending}
                              onClick={() => {
                                voidCode.mutate({ codeId: code.id, reason: reason.trim() });
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
        <MutationErrorNote error={voidCode.error} />
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
                                disabled={!canWrite || downloadCsv.isPending}
                                onClick={() => {
                                  downloadCsv.mutate(
                                    { batchId: batch.batchId, reason: reason.trim() },
                                    {
                                      onSuccess: (blob) => {
                                        downloadBlob(blob, `activation-codes-${batch.batchId}.csv`);
                                      },
                                    },
                                  );
                                }}
                              >
                                {t("activation.downloadCsv")}
                              </Button>
                            ) : null}
                            <Button
                              variant="tertiary"
                              size="sm"
                              leftIcon={<ShieldOff size={14} />}
                              disabled={!canWrite || voidBatch.isPending}
                              onClick={() => {
                                voidBatch.mutate({ batchId: batch.batchId, reason: reason.trim() });
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
        <MutationErrorNote error={voidBatch.error ?? downloadCsv.error} />
      </DensePanel>
    </div>
  );
}
