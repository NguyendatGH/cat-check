import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DensePanel,
  DenseTableFrame,
  DetailGrid,
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
  useAdminPrivacyRequest,
  useAdminPrivacyRequests,
  useCreateAdminPrivacyRequest,
  useTransitionAdminPrivacyRequest,
  isReasonValid,
} from "@/features/admin";
import type { AdminDsarRequest } from "@/features/admin";
import { useGroup1Can } from "./group1Access";

const REQUEST_TYPES = [
  "ACCESS_EXPORT",
  "RECTIFY",
  "ERASE",
  "RESTRICT",
  "OBJECT",
  "WITHDRAW_CONSENT",
  "PROTECTION_MEASURE",
  "COMPLAINT",
] as const;

const STATUSES = ["RECEIVED", "IDENTITY_PENDING", "IN_PROGRESS", "EXTENDED", "COMPLETED", "REJECTED"] as const;
const CHANNELS = ["WEB_FORM", "EMAIL", "POST"] as const;
const TRANSITIONS = ["ACK", "ASSIGN", "EXTEND", "COMPLETE", "REJECT"] as const;
const CLOSED_STATUSES: readonly string[] = ["COMPLETED", "REJECTED"];

function dueAtOf(item: AdminDsarRequest): string {
  return item.extendedTo ?? item.fulfilDueAt;
}

function isOverdue(item: AdminDsarRequest, now: number): boolean {
  return !CLOSED_STATUSES.includes(item.status) && Date.parse(dueAtOf(item)) < now;
}

export function AdminPrivacyRequestsPage() {
  const { t } = useTranslation("admin");
  const can = useGroup1Can();
  const canCreate = can("privacyExport");
  const canTransition = can("privacyRequests");
  const [requestType, setRequestType] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string>();
  const [createUserId, setCreateUserId] = useState("");
  const [createType, setCreateType] = useState("ERASE");
  const [createChannel, setCreateChannel] = useState("WEB_FORM");
  const [createReason, setCreateReason] = useState("");
  const [action, setAction] = useState("ASSIGN");
  const [actionReason, setActionReason] = useState("");
  const [extendedTo, setExtendedTo] = useState("");
  const requests = useAdminPrivacyRequests({ requestType, status, page, size: 20 });
  const detail = useAdminPrivacyRequest(selectedId);
  const create = useCreateAdminPrivacyRequest();
  const transition = useTransitionAdminPrivacyRequest();
  const items = requests.data?.items ?? [];
  const now = Date.now();

  return (
    <div className="flex flex-col gap-4">
      <AdminPageHeader
        title={t("pages.privacyRequests.title")}
        description={t("pages.privacyRequests.description")}
        specRef="L49–L50 · GET /api/v1/admin/privacy/requests"
      />
      <DensePanel title={t("pages.privacyRequests.filtersTitle")}>
        <div className="grid gap-3 md:grid-cols-2">
          <AdminSelect
            label={t("pages.privacyRequests.requestType")}
            value={requestType}
            className="h-10"
            onChange={(event) => {
              setRequestType(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t("pages.privacyRequests.all")}</option>
            {REQUEST_TYPES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </AdminSelect>
          <AdminSelect
            label={t("pages.privacyRequests.status")}
            value={status}
            className="h-10"
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t("pages.privacyRequests.all")}</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </AdminSelect>
        </div>
      </DensePanel>

      {canCreate ? (
        <DensePanel
          title={t("pages.privacyRequests.createTitle")}
          description={t("pages.privacyRequests.createDescription")}
        >
          <div className="grid gap-3 md:grid-cols-[1.2fr_1fr_1fr_auto] md:items-end">
            <Input
              label={t("pages.privacyRequests.userId")}
              value={createUserId}
              autoComplete="off"
              spellCheck={false}
              className="h-10 font-mono"
              onChange={(event) => {
                setCreateUserId(event.target.value);
              }}
            />
            <AdminSelect
              label={t("pages.privacyRequests.type")}
              value={createType}
              className="h-10"
              onChange={(event) => {
                setCreateType(event.target.value);
              }}
            >
              {REQUEST_TYPES.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </AdminSelect>
            <AdminSelect
              label={t("pages.privacyRequests.channel")}
              value={createChannel}
              className="h-10"
              onChange={(event) => {
                setCreateChannel(event.target.value);
              }}
            >
              {CHANNELS.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </AdminSelect>
            <Button
              variant="primary"
              size="sm"
              className="h-10"
              disabled={!createUserId.trim() || !isReasonValid(createReason) || create.isPending}
              loading={create.isPending}
              onClick={() => {
                create.mutate({
                  userId: createUserId.trim(),
                  requestType: createType,
                  channel: createChannel,
                  reason: createReason.trim(),
                });
              }}
            >
              {t("pages.privacyRequests.create")}
            </Button>
          </div>
          <ReasonField value={createReason} onChange={setCreateReason} showError={createReason.length > 0} />
          <MutationErrorNote error={create.error} />
        </DensePanel>
      ) : null}

      {requests.data ? (
        <KpiGrid label={t("dense.kpiLabel")}>
          <KpiTile
            label={t("pages.privacyRequests.kpiTotal")}
            value={formatAdminNumber(requests.data.totalElements)}
            tone="info"
          />
          <KpiTile
            label={t("pages.privacyRequests.kpiOpen")}
            value={formatAdminNumber(items.filter((item) => !CLOSED_STATUSES.includes(item.status)).length)}
            hint={t("dense.onPageHint")}
          />
          <KpiTile
            label={t("pages.privacyRequests.kpiOverdue")}
            value={formatAdminNumber(items.filter((item) => isOverdue(item, now)).length)}
            hint={t("dense.onPageHint")}
            tone={items.some((item) => isOverdue(item, now)) ? "danger" : "neutral"}
          />
          <KpiTile
            label={t("pages.privacyRequests.kpiExtended")}
            value={formatAdminNumber(items.filter((item) => item.status === "EXTENDED").length)}
            hint={t("dense.onPageHint")}
            tone="warning"
          />
        </KpiGrid>
      ) : null}

      <DensePanel title={t("pages.privacyRequests.listTitle")}>
        {requests.isPending ? <TableSkeleton /> : null}
        {requests.isError ? (
          <AdminQueryError
            error={requests.error}
            onRetry={() => {
              void requests.refetch();
            }}
          />
        ) : null}
        {requests.data && items.length === 0 ? (
          <p className="py-6 text-center text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {requests.data && items.length > 0 ? (
          <>
            <DenseTableFrame label={t("pages.privacyRequests.listTitle")}>
              <table className={denseTableClass}>
                <thead>
                  <tr>
                    <th scope="col" className={denseThClass}>
                      {t("pages.privacyRequests.ref")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.privacyRequests.type")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.privacyRequests.status")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.privacyRequests.contact")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.privacyRequests.receivedAt")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.privacyRequests.dueAt")}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => (
                    <tr key={item.id} className={denseRowClass} data-selected={item.id === selectedId}>
                      <td className={denseTdClass}>
                        <button
                          type="button"
                          aria-pressed={item.id === selectedId}
                          className="rounded font-mono text-small font-semibold text-primary-dark hover:underline"
                          onClick={() => {
                            setSelectedId(item.id);
                          }}
                        >
                          {item.publicRef}
                        </button>
                      </td>
                      <td className={`${denseTdClass} font-mono text-small`}>{item.requestType}</td>
                      <td className={`${denseTdClass} font-mono text-small`}>{item.status}</td>
                      <td className={denseTdClass}>{item.contactEmailMasked}</td>
                      <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                        {formatAdminDateTime(item.receivedAt)}
                      </td>
                      <td
                        className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums ${
                          isOverdue(item, now) ? "font-semibold text-danger-text" : ""
                        }`}
                      >
                        {formatAdminDateTime(dueAtOf(item))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </DenseTableFrame>
            <Pager
              page={requests.data.number}
              totalPages={requests.data.totalPages}
              totalElements={requests.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
      </DensePanel>

      {selectedId && detail.isError ? (
        <AdminQueryError
          error={detail.error}
          onRetry={() => {
            void detail.refetch();
          }}
        />
      ) : null}
      {selectedId && detail.data ? (
        <DensePanel title={t("pages.privacyRequests.detailTitle")}>
          <DetailGrid
            items={[
              [t("pages.privacyRequests.ref"), detail.data.publicRef],
              [t("pages.privacyRequests.contact"), detail.data.contactEmailMasked],
              [t("pages.privacyRequests.ackDue"), formatAdminDateTime(detail.data.ackDueAt)],
              [t("pages.privacyRequests.fulfilDue"), formatAdminDateTime(dueAtOf(detail.data))],
              [t("pages.privacyRequests.handledBy"), detail.data.handledBy ?? EMPTY_CELL],
              [t("pages.privacyRequests.rejection"), detail.data.rejectionReason ?? EMPTY_CELL],
            ]}
          />
          {canTransition ? (
            <>
              <div className="grid gap-3 border-t border-border pt-3 md:grid-cols-[1fr_1.5fr_1.5fr_auto] md:items-end">
                <AdminSelect
                  label={t("pages.privacyRequests.action")}
                  value={action}
                  className="h-10"
                  onChange={(event) => {
                    setAction(event.target.value);
                  }}
                >
                  {TRANSITIONS.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </AdminSelect>
                <Input
                  label={t("pages.privacyRequests.extendedToInput")}
                  type={action === "EXTEND" ? "datetime-local" : "text"}
                  disabled={action !== "EXTEND"}
                  value={extendedTo}
                  className="h-10"
                  onChange={(event) => {
                    setExtendedTo(event.target.value);
                  }}
                />
                <Input
                  label={t("pages.privacyRequests.reasonInput")}
                  value={actionReason}
                  className="h-10"
                  onChange={(event) => {
                    setActionReason(event.target.value);
                  }}
                />
                <Button
                  variant="secondary"
                  size="sm"
                  className="h-10"
                  disabled={
                    transition.isPending ||
                    (action === "EXTEND" && (!extendedTo || !isReasonValid(actionReason))) ||
                    (action === "REJECT" && !isReasonValid(actionReason))
                  }
                  loading={transition.isPending}
                  onClick={() => {
                    if (!selectedId) return;
                    transition.mutate({
                      requestId: selectedId,
                      action,
                      reason: actionReason.trim() || undefined,
                      extendedTo: action === "EXTEND" ? new Date(extendedTo).toISOString() : undefined,
                    });
                  }}
                >
                  {t("pages.privacyRequests.apply")}
                </Button>
              </div>
              <MutationErrorNote error={transition.error} />
            </>
          ) : null}
        </DensePanel>
      ) : null}
    </div>
  );
}
