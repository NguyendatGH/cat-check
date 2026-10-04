import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Download, Plus, ShieldOff } from "lucide-react";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminSection,
  AdminSelect,
  AdminTableScroll,
  ApiErrorNote,
  Pager,
  ReasonField,
  adminTableClass,
  adminTdClass,
  adminThClass,
  isReasonValid,
  useAdminActivationBatches,
  useAdminActivationCodes,
  useDownloadActivationBatchCsv,
  useIssueActivationBatch,
  useVoidActivationBatch,
  useVoidActivationCode,
} from "@/features/admin";
import type { ActivationCodeStatus } from "@/features/admin";

const PAGE_SIZE = 20;

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function AdminActivationCodesPage() {
  const { t } = useTranslation("admin");
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
  const canWrite = isReasonValid(reason);

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
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.activationCodes.title")}
        description={t("pages.activationCodes.description")}
        specRef="L19–L24 · GET/POST /api/v1/admin/activation-codes"
        actions={
          <Button
            variant="primary"
            size="md"
            leftIcon={<Plus size={16} />}
            onClick={() => {
              setShowIssueForm((value) => !value);
            }}
          >
            {t("activation.issueButton")}
          </Button>
        }
      />

      {showIssueForm ? (
        <AdminSection title={t("activation.issueTitle")} description={t("activation.issueDescription")}>
          <form
            className="flex flex-col gap-4"
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
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Input
                label={t("activation.packageCode")}
                value={packageCode}
                onChange={(event) => {
                  setPackageCode(event.target.value);
                }}
                required
              />
              <Input
                label={t("activation.productionBatch")}
                value={productionBatch}
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
            <ApiErrorNote error={issue.error} />
            <div className="flex gap-2">
              <Button
                type="submit"
                variant="primary"
                loading={issue.isPending}
                disabled={!canWrite || !packageCode.trim() || !productionBatch.trim()}
              >
                {t("activation.issueSubmit")}
              </Button>
              <Button
                type="button"
                variant="tertiary"
                onClick={() => {
                  setShowIssueForm(false);
                }}
              >
                {t("actions.cancel")}
              </Button>
            </div>
          </form>
        </AdminSection>
      ) : null}

      <AdminSection title={t("activation.filtersTitle")}>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-5">
          <Input
            label={t("activation.prefix")}
            value={prefix}
            onChange={(event) => {
              setPrefix(event.target.value);
              setPage(0);
            }}
          />
          <Input
            label={t("activation.packageCode")}
            value={packageCode}
            onChange={(event) => {
              setPackageCode(event.target.value);
              setPage(0);
            }}
          />
          <AdminSelect
            label={t("activation.statusLabel")}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as ActivationCodeStatus | "");
              setPage(0);
            }}
          >
            <option value="">{t("activation.allStatuses")}</option>
            <option value="ISSUED">{t("activation.status.ISSUED")}</option>
            <option value="REDEEMED">{t("activation.status.REDEEMED")}</option>
            <option value="VOID">{t("activation.status.VOID")}</option>
          </AdminSelect>
          <Input
            label={t("activation.batchId")}
            value={batchId}
            onChange={(event) => {
              setBatchId(event.target.value);
              setPage(0);
            }}
          />
          <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
        </div>
      </AdminSection>

      <AdminSection title={t("activation.codesTitle")}>
        {codes.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {codes.isError ? <ApiErrorNote error={codes.error} /> : null}
        {codes.data && codes.data.items.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {codes.data && codes.data.items.length > 0 ? (
          <>
            <AdminTableScroll>
              <table className={adminTableClass}>
                <thead>
                  <tr>
                    <th className={adminThClass}>{t("activation.colPrefix")}</th>
                    <th className={adminThClass}>{t("activation.colPackage")}</th>
                    <th className={adminThClass}>{t("activation.colBatch")}</th>
                    <th className={adminThClass}>{t("activation.colIssued")}</th>
                    <th className={adminThClass}>{t("activation.colValidUntil")}</th>
                    <th className={adminThClass}>{t("activation.colStatus")}</th>
                    <th className={adminThClass}>{t("activation.colAction")}</th>
                  </tr>
                </thead>
                <tbody>
                  {codes.data.items.map((code) => (
                    <tr key={code.id}>
                      <td className={adminTdClass}>
                        <code>{code.codePrefix}••••</code>
                      </td>
                      <td className={adminTdClass}>{code.packageCode}</td>
                      <td className={adminTdClass}>{code.productionBatch}</td>
                      <td className={adminTdClass}>{formatDate(code.issuedAt)}</td>
                      <td className={adminTdClass}>{formatDate(code.validUntil)}</td>
                      <td className={adminTdClass}>{t(`activation.status.${code.status}`)}</td>
                      <td className={adminTdClass}>
                        {code.status === "ISSUED" ? (
                          <Button
                            variant="tertiary"
                            size="sm"
                            leftIcon={<ShieldOff size={15} />}
                            disabled={!canWrite || voidCode.isPending}
                            onClick={() => {
                              voidCode.mutate({ codeId: code.id, reason: reason.trim() });
                            }}
                          >
                            {t("activation.void")}
                          </Button>
                        ) : (
                          "—"
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
            <Pager
              page={codes.data.number}
              totalPages={codes.data.totalPages}
              totalElements={codes.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
        <ApiErrorNote error={voidCode.error} />
      </AdminSection>

      <AdminSection title={t("activation.batchesTitle")}>
        {batches.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {batches.isError ? <ApiErrorNote error={batches.error} /> : null}
        {batches.data && batches.data.items.length > 0 ? (
          <>
            <AdminTableScroll>
              <table className={adminTableClass}>
                <thead>
                  <tr>
                    <th className={adminThClass}>{t("activation.colBatch")}</th>
                    <th className={adminThClass}>{t("activation.colPackage")}</th>
                    <th className={adminThClass}>{t("activation.colTotals")}</th>
                    <th className={adminThClass}>{t("activation.colIssued")}</th>
                    <th className={adminThClass}>{t("activation.colValidUntil")}</th>
                    <th className={adminThClass}>{t("activation.colAction")}</th>
                  </tr>
                </thead>
                <tbody>
                  {batches.data.items.map((batch) => (
                    <tr key={batch.batchId}>
                      <td className={adminTdClass}>{batch.productionBatch}</td>
                      <td className={adminTdClass}>{batch.packageCode}</td>
                      <td className={adminTdClass}>
                        {batch.totalCodes} / {batch.redeemedCodes} / {batch.voidedCodes}
                      </td>
                      <td className={adminTdClass}>{formatDate(batch.issuedAt)}</td>
                      <td className={adminTdClass}>{formatDate(batch.validUntil)}</td>
                      <td className={adminTdClass}>
                        <div className="flex flex-wrap gap-2">
                          {batch.csvAvailable ? (
                            <Button
                              variant="tertiary"
                              size="sm"
                              leftIcon={<Download size={15} />}
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
                            leftIcon={<ShieldOff size={15} />}
                            disabled={!canWrite || voidBatch.isPending}
                            onClick={() => {
                              voidBatch.mutate({ batchId: batch.batchId, reason: reason.trim() });
                            }}
                          >
                            {t("activation.voidBatch")}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
            <Pager
              page={batches.data.number}
              totalPages={batches.data.totalPages}
              totalElements={batches.data.totalElements}
              onPageChange={setBatchPage}
            />
          </>
        ) : null}
        <ApiErrorNote error={voidBatch.error ?? downloadCsv.error} />
      </AdminSection>
    </div>
  );
}
