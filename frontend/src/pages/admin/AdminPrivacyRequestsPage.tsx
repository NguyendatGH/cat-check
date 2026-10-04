import { useState } from "react";
import { useTranslation } from "react-i18next";
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
  useAdminPrivacyRequest,
  useAdminPrivacyRequests,
  useCreateAdminPrivacyRequest,
  useTransitionAdminPrivacyRequest,
  isReasonValid,
} from "@/features/admin";

function formatDate(value: string | null): string {
  return value
    ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value))
    : "—";
}

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

export function AdminPrivacyRequestsPage() {
  const { t } = useTranslation("admin");
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

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.privacyRequests.title")}
        description={t("pages.privacyRequests.description")}
        specRef="L49–L50 · GET /api/v1/admin/privacy/requests"
      />
      <AdminSection title={t("privacyRequests.filtersTitle")}>
        <div className="grid gap-4 md:grid-cols-2">
          <AdminSelect
            label={t("privacyRequests.requestType")}
            value={requestType}
            onChange={(event) => {
              setRequestType(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t("privacyRequests.all")}</option>
            {REQUEST_TYPES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </AdminSelect>
          <AdminSelect
            label={t("privacyRequests.status")}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t("privacyRequests.all")}</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </AdminSelect>
        </div>
      </AdminSection>

      <AdminSection title={t("privacyRequests.createTitle")} description={t("privacyRequests.createDescription")}>
        <div className="grid gap-3 md:grid-cols-[1.2fr_1fr_1fr_auto] md:items-end">
          <Input label={t("privacyRequests.userId")} value={createUserId} onChange={(event) => { setCreateUserId(event.target.value); }} />
          <label className="flex flex-col gap-1 text-caption font-medium text-text-secondary">
            {t("privacyRequests.type")}
            <select className="h-11 rounded-xl border border-border bg-surface px-3 text-body text-text-primary" value={createType} onChange={(event) => { setCreateType(event.target.value); }}>
              {REQUEST_TYPES.map((value) => <option key={value}>{value}</option>)}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-caption font-medium text-text-secondary">
            {t("privacyRequests.channel")}
            <select className="h-11 rounded-xl border border-border bg-surface px-3 text-body text-text-primary" value={createChannel} onChange={(event) => { setCreateChannel(event.target.value); }}>
              {(["WEB_FORM", "EMAIL", "POST"] as const).map((value) => <option key={value}>{value}</option>)}
            </select>
          </label>
          <Button variant="primary" disabled={!createUserId.trim() || !isReasonValid(createReason) || create.isPending} loading={create.isPending} onClick={() => { create.mutate({ userId: createUserId.trim(), requestType: createType, channel: createChannel, reason: createReason.trim() }); }}>
            {t("privacyRequests.create")}
          </Button>
        </div>
        <ReasonField value={createReason} onChange={setCreateReason} showError={createReason.length > 0} />
        <ApiErrorNote error={create.error} />
      </AdminSection>

      <AdminSection title={t("privacyRequests.listTitle")}>
        {requests.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {requests.isError ? <ApiErrorNote error={requests.error} /> : null}
        {requests.data && requests.data.items.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {requests.data && requests.data.items.length > 0 ? (
          <>
            <AdminTableScroll>
              <table className={adminTableClass}>
                <thead>
                  <tr>
                    <th className={adminThClass}>{t("privacyRequests.ref")}</th>
                    <th className={adminThClass}>{t("privacyRequests.type")}</th>
                    <th className={adminThClass}>{t("privacyRequests.status")}</th>
                    <th className={adminThClass}>{t("privacyRequests.contact")}</th>
                    <th className={adminThClass}>{t("privacyRequests.receivedAt")}</th>
                    <th className={adminThClass}>{t("privacyRequests.dueAt")}</th>
                  </tr>
                </thead>
                <tbody>
                  {requests.data.items.map((item) => (
                    <tr
                      key={item.id}
                      className="cursor-pointer hover:bg-background-alt"
                      onClick={() => {
                        setSelectedId(item.id);
                      }}
                    >
                      <td className={adminTdClass}>
                        <code>{item.publicRef}</code>
                      </td>
                      <td className={adminTdClass}>{item.requestType}</td>
                      <td className={adminTdClass}>{item.status}</td>
                      <td className={adminTdClass}>{item.contactEmailMasked}</td>
                      <td className={adminTdClass}>{formatDate(item.receivedAt)}</td>
                      <td className={adminTdClass}>{formatDate(item.extendedTo ?? item.fulfilDueAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
            <Pager
              page={requests.data.number}
              totalPages={requests.data.totalPages}
              totalElements={requests.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
      </AdminSection>

      {selectedId && detail.data ? (
        <AdminSection title={t("privacyRequests.detailTitle")}>
          <dl className="grid gap-3 sm:grid-cols-2">
            <div>
              <dt className="text-small text-text-secondary">{t("privacyRequests.ref")}</dt>
              <dd className="font-semibold">{detail.data.publicRef}</dd>
            </div>
            <div>
              <dt className="text-small text-text-secondary">{t("privacyRequests.contact")}</dt>
              <dd>{detail.data.contactEmailMasked}</dd>
            </div>
            <div>
              <dt className="text-small text-text-secondary">{t("privacyRequests.ackDue")}</dt>
              <dd>{formatDate(detail.data.ackDueAt)}</dd>
            </div>
            <div>
              <dt className="text-small text-text-secondary">{t("privacyRequests.fulfilDue")}</dt>
              <dd>{formatDate(detail.data.extendedTo ?? detail.data.fulfilDueAt)}</dd>
            </div>
            <div>
              <dt className="text-small text-text-secondary">{t("privacyRequests.handledBy")}</dt>
              <dd>{detail.data.handledBy ?? "—"}</dd>
            </div>
            <div>
              <dt className="text-small text-text-secondary">{t("privacyRequests.rejection")}</dt>
              <dd>{detail.data.rejectionReason ?? "—"}</dd>
            </div>
          </dl>
          <div className="grid gap-3 border-t border-border pt-4 md:grid-cols-[1fr_1.5fr_1.5fr_auto] md:items-end">
            <label className="flex flex-col gap-1 text-caption font-medium text-text-secondary">
              {t("privacyRequests.action")}
              <select className="h-11 rounded-xl border border-border bg-surface px-3 text-body text-text-primary" value={action} onChange={(event) => { setAction(event.target.value); }}>
                {(["ACK", "ASSIGN", "EXTEND", "COMPLETE", "REJECT"] as const).map((value) => <option key={value}>{value}</option>)}
              </select>
            </label>
            <Input label={t("privacyRequests.extendedToInput")} type={action === "EXTEND" ? "datetime-local" : "text"} disabled={action !== "EXTEND"} value={extendedTo} onChange={(event) => { setExtendedTo(event.target.value); }} />
            <Input label={t("privacyRequests.reasonInput")} value={actionReason} onChange={(event) => { setActionReason(event.target.value); }} />
            <Button variant="secondary" disabled={transition.isPending || (action === "EXTEND" && (!extendedTo || !isReasonValid(actionReason))) || (action === "REJECT" && !isReasonValid(actionReason))} loading={transition.isPending} onClick={() => {
              if (!selectedId) return;
              transition.mutate({ requestId: selectedId, action, reason: actionReason.trim() || undefined, extendedTo: action === "EXTEND" ? new Date(extendedTo).toISOString() : undefined });
            }}>{t("privacyRequests.apply")}</Button>
          </div>
          <ApiErrorNote error={transition.error} />
        </AdminSection>
      ) : null}
    </div>
  );
}
