import { useState } from "react";
import { Link } from "react-router";
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
  ReasonConfirmDialog,
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
  useAdminPrivacyRequestSummary,
  useCreateAdminPrivacyRequest,
  useTransitionAdminPrivacyRequest,
  isReasonValid,
} from "@/features/admin";
import { useGroup1Can } from "./group1Access";
import {
  CREATE_CHANNELS,
  IRREVERSIBLE_TRANSITIONS,
  REQUEST_TYPES,
  STATUSES,
  allowedActionsFor,
  currentPageOf,
  dueAtOf,
  isClosed,
  isOverdue,
} from "./privacyRequestsModel";
import {
  CountdownTag,
  PrivacyRequestsExplainer,
  StatusTag,
  TypeTag,
  UserPicker,
  type SelectedUser,
} from "./privacyRequestsParts";

const P = "pages.privacyRequests";

export function AdminPrivacyRequestsPage() {
  const { t } = useTranslation("admin");
  const can = useGroup1Can();
  // privacyExport = ADMIN_SUPER/DPO — đúng tập role được tạo yêu cầu và dùng mọi thao tác ở backend.
  const canCreate = can("privacyExport");
  const canTransition = can("privacyRequests");
  const [confirmingTransition, setConfirmingTransition] = useState(false);
  const [requestType, setRequestType] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string>();
  const [createUser, setCreateUser] = useState<SelectedUser>();
  const [createType, setCreateType] = useState("ERASE");
  const [createChannel, setCreateChannel] = useState("EMAIL");
  const [createReason, setCreateReason] = useState("");
  const [action, setAction] = useState("ASSIGN");
  const [actionReason, setActionReason] = useState("");
  const [extendedTo, setExtendedTo] = useState("");
  const requests = useAdminPrivacyRequests({ requestType, status, page, size: 20 });
  const detail = useAdminPrivacyRequest(selectedId);
  const summary = useAdminPrivacyRequestSummary(requestType);
  const summaryValue = (value: number | undefined) => (value === undefined ? "—" : formatAdminNumber(value));
  const create = useCreateAdminPrivacyRequest();
  const transition = useTransitionAdminPrivacyRequest();
  const items = requests.data?.items ?? [];
  const now = Date.now();
  const typeLabel = (value: string) => t(`${P}.types.${value}`, { defaultValue: value });

  const allowedActions = detail.data ? allowedActionsFor(detail.data, canCreate) : [];
  const currentAction = allowedActions.includes(action) ? action : (allowedActions[0] ?? action);

  return (
    <div className="flex flex-col gap-4">
      <AdminPageHeader title={t(`${P}.title`)} description={t(`${P}.description`)} />
      <PrivacyRequestsExplainer />

      <DensePanel title={t(`${P}.filtersTitle`)}>
        <div className="grid gap-3 md:grid-cols-2">
          <AdminSelect
            label={t(`${P}.requestType`)}
            value={requestType}
            className="h-10"
            onChange={(event) => {
              setRequestType(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t(`${P}.all`)}</option>
            {REQUEST_TYPES.map((value) => (
              <option key={value} value={value}>
                {typeLabel(value)}
              </option>
            ))}
          </AdminSelect>
          <AdminSelect
            label={t(`${P}.status`)}
            value={status}
            className="h-10"
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">{t(`${P}.all`)}</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {t(`${P}.statuses.${value}`)}
              </option>
            ))}
          </AdminSelect>
        </div>
      </DensePanel>

      {canCreate ? (
        <DensePanel title={t(`${P}.createTitle`)} description={t(`${P}.createDescription`)}>
          <UserPicker value={createUser} onChange={setCreateUser} />
          <div className="grid gap-3 md:grid-cols-2">
            <AdminSelect
              label={t(`${P}.type`)}
              value={createType}
              className="h-10"
              onChange={(event) => {
                setCreateType(event.target.value);
              }}
            >
              {REQUEST_TYPES.map((value) => (
                <option key={value} value={value}>
                  {typeLabel(value)}
                </option>
              ))}
            </AdminSelect>
            <AdminSelect
              label={t(`${P}.channel`)}
              value={createChannel}
              className="h-10"
              onChange={(event) => {
                setCreateChannel(event.target.value);
              }}
            >
              {CREATE_CHANNELS.map((value) => (
                <option key={value} value={value}>
                  {t(`${P}.channels.${value}`)}
                </option>
              ))}
            </AdminSelect>
          </div>
          <p className="text-caption text-text-secondary">{t(`${P}.typeHints.${createType}`)}</p>
          <ReasonField value={createReason} onChange={setCreateReason} showError={createReason.length > 0} />
          <div className="flex flex-wrap items-center gap-3">
            <Button
              variant="primary"
              size="sm"
              className="h-10"
              disabled={!createUser || !isReasonValid(createReason) || create.isPending}
              loading={create.isPending}
              onClick={() => {
                if (!createUser) return;
                create.mutate(
                  {
                    userId: createUser.userId,
                    requestType: createType,
                    channel: createChannel,
                    reason: createReason.trim(),
                  },
                  {
                    onSuccess: (created) => {
                      setCreateUser(undefined);
                      setCreateReason("");
                      setSelectedId(created.id);
                    },
                  },
                );
              }}
            >
              {t(`${P}.create`)}
            </Button>
            {!createUser ? (
              <span className="text-caption text-text-tertiary">{t(`${P}.userLookup.required`)}</span>
            ) : null}
          </div>
          <MutationErrorNote error={create.error} />
          {create.isSuccess ? (
            <p role="status" className="text-caption text-success-text">
              {t(`${P}.created`, { ref: create.data.publicRef })}
            </p>
          ) : null}
        </DensePanel>
      ) : null}

      {requests.data ? (
        <div className="flex flex-col gap-2">
          <KpiGrid label={t("dense.kpiLabel")}>
            <KpiTile
              label={t(`${P}.kpiTotal`)}
              value={formatAdminNumber(requests.data.totalElements)}
              hint={t(`${P}.kpiHints.total`)}
              tone="info"
            />
            <KpiTile
              label={t(`${P}.kpiOpen`)}
              value={summaryValue(summary.data?.open)}
              hint={t(`${P}.kpiHints.open`)}
            />
            <KpiTile
              label={t(`${P}.kpiOverdue`)}
              value={summaryValue(summary.data?.overdue)}
              hint={t(`${P}.kpiHints.overdue`)}
              tone={(summary.data?.overdue ?? 0) > 0 ? "danger" : "neutral"}
            />
            <KpiTile
              label={t(`${P}.kpiExtended`)}
              value={summaryValue(summary.data?.extended)}
              hint={t(`${P}.kpiHints.extended`)}
              tone="warning"
            />
          </KpiGrid>
          <p className="text-caption text-text-tertiary">{t(`${P}.kpiNote`)}</p>
        </div>
      ) : null}

      <DensePanel title={t(`${P}.listTitle`)}>
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
            <ul className="flex flex-col gap-2 md:hidden">
              {items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    aria-pressed={item.id === selectedId}
                    className="flex w-full flex-col gap-2 rounded-lg border border-border px-3 py-2.5 text-left aria-pressed:border-primary aria-pressed:bg-chip-bg/40"
                    onClick={() => {
                      setSelectedId(item.id);
                    }}
                  >
                    <span className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-mono text-small font-semibold text-primary-dark">{item.publicRef}</span>
                      <StatusTag value={item.status} />
                    </span>
                    <span className="flex flex-wrap items-center gap-2">
                      <TypeTag value={item.requestType} />
                      <CountdownTag item={item} now={now} />
                    </span>
                    <span className="text-small text-text-secondary">
                      {item.contactEmailMasked} · {t(`${P}.dueAt`)} {formatAdminDateTime(dueAtOf(item))}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
            <div className="hidden md:block">
              <DenseTableFrame label={t(`${P}.listTitle`)}>
                <table className={denseTableClass}>
                  <thead>
                    <tr>
                      <th scope="col" className={denseThClass}>
                        {t(`${P}.ref`)}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t(`${P}.type`)}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t(`${P}.status`)}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t(`${P}.contact`)}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t(`${P}.receivedAt`)}
                      </th>
                      <th scope="col" className={denseThClass}>
                        {t(`${P}.dueAt`)}
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
                        <td className={denseTdClass}>
                          <TypeTag value={item.requestType} />
                        </td>
                        <td className={`${denseTdClass} whitespace-nowrap`}>
                          <StatusTag value={item.status} />
                        </td>
                        <td className={denseTdClass}>{item.contactEmailMasked}</td>
                        <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                          {formatAdminDateTime(item.receivedAt)}
                        </td>
                        <td className={`${denseTdClass} whitespace-nowrap`}>
                          <span
                            className={`block font-mono tabular-nums ${
                              isOverdue(item, now) ? "font-semibold text-danger-text" : ""
                            }`}
                          >
                            {formatAdminDateTime(dueAtOf(item))}
                          </span>
                          <CountdownTag item={item} now={now} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </DenseTableFrame>
            </div>
            <Pager
              page={currentPageOf(requests.data, page)}
              totalPages={requests.data.totalPages}
              totalElements={requests.data.totalElements}
              onPageChange={setPage}
            />
          </>
        ) : null}
      </DensePanel>

      {selectedId && detail.isPending ? <TableSkeleton rows={3} /> : null}
      {selectedId && detail.isError ? (
        <AdminQueryError
          error={detail.error}
          onRetry={() => {
            void detail.refetch();
          }}
        />
      ) : null}
      {selectedId && detail.data ? (
        <DensePanel title={t(`${P}.detailTitle`)}>
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-small font-semibold text-text-primary">{detail.data.publicRef}</span>
            <TypeTag value={detail.data.requestType} />
            <StatusTag value={detail.data.status} />
            <CountdownTag item={detail.data} now={now} />
          </div>
          <p className="text-caption text-text-secondary">
            {t(`${P}.statusHints.${detail.data.status}`, { defaultValue: "" })}{" "}
            {t(`${P}.typeHints.${detail.data.requestType}`, { defaultValue: "" })}
          </p>
          <DetailGrid
            items={[
              [t(`${P}.ref`), detail.data.publicRef],
              [t(`${P}.type`), typeLabel(detail.data.requestType)],
              [t(`${P}.status`), t(`${P}.statuses.${detail.data.status}`, { defaultValue: detail.data.status })],
              [t(`${P}.channel`), t(`${P}.channels.${detail.data.channel}`, { defaultValue: detail.data.channel })],
              [t(`${P}.contact`), detail.data.contactEmailMasked],
              [t(`${P}.receivedAt`), formatAdminDateTime(detail.data.receivedAt)],
              [t(`${P}.ackDue`), formatAdminDateTime(detail.data.ackDueAt)],
              [t(`${P}.ackSent`), formatAdminDateTime(detail.data.ackSentAt)],
              [t(`${P}.fulfilDue`), formatAdminDateTime(dueAtOf(detail.data))],
              [t(`${P}.extensionReason`), detail.data.extensionReason ?? EMPTY_CELL],
              [t(`${P}.handledBy`), detail.data.handledBy ?? EMPTY_CELL],
              [t(`${P}.completedAt`), formatAdminDateTime(detail.data.completedAt)],
              [t(`${P}.rejection`), detail.data.rejectionReason ?? EMPTY_CELL],
            ]}
          />
          {detail.data.userId ? (
            <Link
              to={`/admin/users/${encodeURIComponent(detail.data.userId)}`}
              className="self-start text-caption font-semibold text-primary-dark hover:underline"
            >
              {t(`${P}.viewUser`)}
            </Link>
          ) : null}
          {canTransition && isClosed(detail.data.status) ? (
            <p className="border-t border-border pt-3 text-caption text-text-secondary">{t(`${P}.closedNote`)}</p>
          ) : null}
          {canTransition && !isClosed(detail.data.status) ? (
            <>
              <div className="grid gap-3 border-t border-border pt-3 md:grid-cols-[1fr_1.5fr_1.5fr_auto] md:items-end">
                <AdminSelect
                  label={t(`${P}.action`)}
                  value={currentAction}
                  className="h-10"
                  onChange={(event) => {
                    setAction(event.target.value);
                  }}
                >
                  {allowedActions.map((value) => (
                    <option key={value} value={value}>
                      {t(`${P}.transitions.${value}`)}
                    </option>
                  ))}
                </AdminSelect>
                <Input
                  label={t(`${P}.extendedToInput`)}
                  type={currentAction === "EXTEND" ? "datetime-local" : "text"}
                  disabled={currentAction !== "EXTEND"}
                  value={extendedTo}
                  className="h-10"
                  onChange={(event) => {
                    setExtendedTo(event.target.value);
                  }}
                />
                <Input
                  label={t(`${P}.reasonInput`)}
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
                    (currentAction === "EXTEND" && (!extendedTo || !isReasonValid(actionReason))) ||
                    (currentAction === "REJECT" && !isReasonValid(actionReason))
                  }
                  loading={transition.isPending}
                  onClick={() => {
                    if (!selectedId) return;
                    if (IRREVERSIBLE_TRANSITIONS.includes(currentAction)) {
                      transition.reset();
                      setConfirmingTransition(true);
                      return;
                    }
                    transition.mutate({
                      requestId: selectedId,
                      action: currentAction,
                      reason: actionReason.trim() || undefined,
                      extendedTo: currentAction === "EXTEND" ? new Date(extendedTo).toISOString() : undefined,
                    });
                  }}
                >
                  {t(`${P}.apply`)}
                </Button>
              </div>
              <p className="text-caption text-text-secondary">
                {t(`${P}.transitionHints.${currentAction}`, { defaultValue: "" })}
              </p>
              {detail.data.requestType === "ERASE" ? (
                <p className="text-caption text-warning-text">{t(`${P}.eraseNoComplete`)}</p>
              ) : null}
              {detail.data.requestType === "ACCESS_EXPORT" && canCreate ? (
                <p className="text-caption text-text-secondary">{t(`${P}.exportAutoComplete`)}</p>
              ) : null}
              {confirmingTransition ? null : <MutationErrorNote error={transition.error} />}
              <ReasonConfirmDialog
                open={confirmingTransition}
                onOpenChange={setConfirmingTransition}
                title={t(`${P}.transitions.${currentAction}`, { defaultValue: currentAction })}
                target={`${detail.data.publicRef} · ${typeLabel(detail.data.requestType)}`}
                consequence={
                  currentAction === "REJECT" ? t(`${P}.rejectConsequence`) : t(`${P}.completeConsequence`)
                }
                confirmLabel={t(`${P}.transitions.${currentAction}`, { defaultValue: currentAction })}
                requireReason={false}
                destructive={currentAction === "REJECT"}
                pending={transition.isPending}
                error={transition.error}
                onConfirm={() => {
                  if (!selectedId) return;
                  transition.mutate(
                    { requestId: selectedId, action: currentAction, reason: actionReason.trim() || undefined },
                    {
                      onSuccess: () => {
                        setConfirmingTransition(false);
                      },
                    },
                  );
                }}
              />
            </>
          ) : null}
        </DensePanel>
      ) : null}
    </div>
  );
}
