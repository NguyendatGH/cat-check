import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Check, Lock } from "lucide-react";
import { useSessionStore } from "@/entities/user";
import { Button, Input } from "@/shared/ui";
import {
  AccessDeniedState,
  AdminPageHeader,
  AdminQueryError,
  AdminSelect,
  DensePanel,
  DenseTableFrame,
  MonoId,
  MutationErrorNote,
  ReasonConfirmDialog,
  SearchField,
  SortableTh,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  isUuid,
  matchesQuery,
  nextSort,
  sortRows,
  useAdminCan,
  useAdminMfaResetRequests,
  useApproveAdminMfaReset,
  useGrantAdminRole,
  useRequestAdminMfaReset,
  useRevokeAdminRole,
  type AdminMfaResetRequest,
  type SortState,
} from "@/features/admin";

const STAFF_ROLES = ["ADMIN_SUPPORT", "ADMIN_CATALOG", "MODERATOR", "VET"] as const;

type StaffCapability = "staffRolesWrite" | "totpResetRequestsRead" | "totpResetApprove";

function useStaffCan(): (key: StaffCapability) => boolean {
  const can = useAdminCan();
  return (key) => can(key);
}

type ApprovalState = "allowed" | "self" | "expired" | "closed";

function approvalState(item: AdminMfaResetRequest, currentUserId: string | undefined, now: number): ApprovalState {
  if (item.status !== "PENDING") return "closed";
  if (currentUserId !== undefined && item.requestedBy === currentUserId) return "self";
  const expiresAt = Date.parse(item.expiresAt);
  if (!Number.isNaN(expiresAt) && expiresAt <= now) return "expired";
  return "allowed";
}

type QueueSortKey = "requestedAt" | "expiresAt";

function queueSortValue(item: AdminMfaResetRequest, key: QueueSortKey): string {
  return key === "requestedAt" ? item.requestedAt : item.expiresAt;
}

function ReadOnlyNote({ text }: { text: string }) {
  return (
    <p className="flex items-center gap-2 rounded-lg bg-background-alt px-3 py-2 text-caption text-text-secondary">
      <Lock size={14} aria-hidden="true" className="shrink-0" />
      {text}
    </p>
  );
}

export function AdminStaffPage() {
  const { t } = useTranslation("admin");
  const can = useStaffCan();
  const canWriteRoles = can("staffRolesWrite");
  const canReadQueue = can("totpResetRequestsRead");
  const canApprove = can("totpResetApprove");

  const header = <AdminPageHeader title={t("pages.staff.title")} description={t("pages.staff.description")} />;

  if (!canWriteRoles && !canReadQueue) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <AccessDeniedState />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {header}
      {canWriteRoles ? <StaffRolePanel /> : <ReadOnlyNote text={t("pages.staff.rolesReadOnly")} />}
      {canReadQueue ? <ResetQueuePanel canApprove={canApprove} /> : null}
    </div>
  );
}

type RoleAction = "grant" | "revoke" | "requestReset";

function StaffRolePanel() {
  const { t } = useTranslation("admin");
  const [userId, setUserId] = useState("");
  const [role, setRole] = useState<string>(STAFF_ROLES[0]);
  const [pending, setPending] = useState<RoleAction | null>(null);
  const grant = useGrantAdminRole();
  const revoke = useRevokeAdminRole();
  const requestReset = useRequestAdminMfaReset();
  const canSubmit = isUuid(userId.trim());
  const mutation = pending === "grant" ? grant : pending === "revoke" ? revoke : requestReset;
  const roleLabel = t(`roleLabels.${role}`, { defaultValue: role });
  const lastSuccess = grant.isSuccess || revoke.isSuccess || requestReset.isSuccess;

  const open = (action: RoleAction) => {
    grant.reset();
    revoke.reset();
    requestReset.reset();
    setPending(action);
  };

  return (
    <DensePanel title={t("pages.staff.roleTitle")} description={t("pages.staff.roleDescription")}>
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_220px] md:items-end">
        <Input
          label={t("pages.staff.userId")}
          className="h-10 font-mono"
          value={userId}
          autoComplete="off"
          spellCheck={false}
          error={userId.trim().length > 0 && !canSubmit ? t("pages.staff.userIdInvalid") : undefined}
          onChange={(event) => {
            setUserId(event.target.value);
          }}
        />
        <AdminSelect
          label={t("pages.staff.role")}
          className="h-10"
          value={role}
          onChange={(event) => {
            setRole(event.target.value);
          }}
        >
          {STAFF_ROLES.map((item) => (
            <option key={item} value={item}>
              {t(`roleLabels.${item}`, { defaultValue: item })}
            </option>
          ))}
        </AdminSelect>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="secondary"
          size="sm"
          disabled={!canSubmit || grant.isPending}
          onClick={() => {
            open("grant");
          }}
        >
          {t("pages.staff.grant")}
        </Button>
        <Button
          variant="tertiary"
          size="sm"
          disabled={!canSubmit || revoke.isPending}
          onClick={() => {
            open("revoke");
          }}
        >
          {t("pages.staff.revoke")}
        </Button>
        <span className="mx-1 hidden h-6 w-px bg-border sm:block" aria-hidden="true" />
        <Button
          variant="tertiary"
          size="sm"
          disabled={!canSubmit || requestReset.isPending}
          onClick={() => {
            open("requestReset");
          }}
        >
          {t("pages.staff.requestReset")}
        </Button>
      </div>
      {lastSuccess && pending === null ? (
        <p role="status" className="text-caption text-success-text">
          {t("feedback.saved")}
        </p>
      ) : null}
      <ReasonConfirmDialog
        open={pending !== null}
        onOpenChange={(next) => {
          if (!next) setPending(null);
        }}
        title={
          pending === "grant"
            ? t("pages.staff.grant")
            : pending === "revoke"
              ? t("pages.staff.revoke")
              : t("pages.staff.requestReset")
        }
        target={pending === "requestReset" ? userId.trim() : `${userId.trim()} · ${roleLabel}`}
        consequence={
          pending === "grant"
            ? t("pages.staff.grantConsequence", { role: roleLabel })
            : pending === "revoke"
              ? t("pages.staff.revokeConsequence", { role: roleLabel })
              : t("pages.staff.requestResetConsequence")
        }
        confirmLabel={
          pending === "grant"
            ? t("pages.staff.grant")
            : pending === "revoke"
              ? t("pages.staff.revoke")
              : t("pages.staff.requestReset")
        }
        // Backend L14 (gỡ role) không nhận `reason`.
        requireReason={pending !== "revoke"}
        destructive={pending !== "grant"}
        pending={mutation.isPending}
        error={mutation.error}
        onConfirm={(reason) => {
          const done = {
            onSuccess: () => {
              setPending(null);
            },
          };
          const target = userId.trim();
          if (pending === "grant") grant.mutate({ userId: target, role, reason }, done);
          else if (pending === "revoke") revoke.mutate({ userId: target, role }, done);
          else requestReset.mutate({ userId: target, reason }, done);
        }}
      />
    </DensePanel>
  );
}

function ResetQueuePanel({ canApprove }: { canApprove: boolean }) {
  const { t } = useTranslation("admin");
  const requests = useAdminMfaResetRequests();
  const approve = useApproveAdminMfaReset();
  const currentUserId = useSessionStore((state) => state.user?.id);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortState<QueueSortKey> | null>({ key: "requestedAt", direction: "asc" });
  const [confirmItem, setConfirmItem] = useState<AdminMfaResetRequest | null>(null);

  const items = useMemo(() => requests.data ?? [], [requests.data]);
  const visible = useMemo(
    () =>
      sortRows(
        items.filter((item) => matchesQuery([item.targetUserId, item.requestedBy, item.reason], query)),
        sort,
        queueSortValue,
      ),
    [items, query, sort],
  );
  const onSort = (key: QueueSortKey) => {
    setSort((current) => nextSort(current, key));
  };
  const now = Date.now();

  return (
    <DensePanel title={t("pages.staff.queueTitle")} description={t("pages.staff.queueDescription")}>
      {!canApprove ? <ReadOnlyNote text={t("pages.staff.approveReadOnly")} /> : null}
      {requests.isPending ? (
        <TableSkeleton rows={3} />
      ) : requests.isError ? (
        <AdminQueryError
          error={requests.error}
          onRetry={() => {
            void requests.refetch();
          }}
        />
      ) : items.length === 0 ? (
        <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
      ) : (
        <>
          <div className="max-w-md">
            <SearchField label={t("dense.search")} value={query} onChange={setQuery} />
          </div>
          {visible.length === 0 ? (
            <p className="text-caption text-text-secondary">{t("dense.noMatch")}</p>
          ) : (
            <DenseTableFrame label={t("pages.staff.queueTitle")}>
              <table className={denseTableClass}>
                <thead>
                  <tr>
                    <th scope="col" className={denseThClass}>
                      {t("pages.staff.target")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.staff.requester")}
                    </th>
                    <th scope="col" className={denseThClass}>
                      {t("pages.staff.reason")}
                    </th>
                    <SortableTh
                      label={t("pages.staff.requestedAt")}
                      sortKey="requestedAt"
                      sort={sort}
                      onSort={onSort}
                    />
                    <SortableTh label={t("pages.staff.expiresAt")} sortKey="expiresAt" sort={sort} onSort={onSort} />
                    {canApprove ? (
                      <th scope="col" className={`${denseThClass} text-right`}>
                        {t("pages.staff.action")}
                      </th>
                    ) : null}
                  </tr>
                </thead>
                <tbody>
                  {visible.map((item) => {
                    const state = approvalState(item, currentUserId, now);
                    return (
                      <tr key={item.id} className={denseRowClass}>
                        <td className={denseTdClass}>
                          <MonoId value={item.targetUserId} />
                        </td>
                        <td className={denseTdClass}>
                          <MonoId value={item.requestedBy} />
                        </td>
                        <td className={denseTdClass}>
                          <span className="block max-w-[320px] truncate" title={item.reason}>
                            {item.reason}
                          </span>
                        </td>
                        <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                          {formatAdminDateTime(item.requestedAt)}
                        </td>
                        <td className={`${denseTdClass} whitespace-nowrap font-mono tabular-nums`}>
                          {formatAdminDateTime(item.expiresAt)}
                        </td>
                        {canApprove ? (
                          <td className={`${denseTdClass} whitespace-nowrap text-right`}>
                            {state === "allowed" ? (
                              <Button
                                variant="tertiary"
                                size="sm"
                                className="min-h-8 px-2"
                                disabled={approve.isPending}
                                onClick={() => {
                                  approve.reset();
                                  setConfirmItem(item);
                                }}
                              >
                                <Check size={14} aria-hidden="true" />
                                {t("pages.staff.approve")}
                              </Button>
                            ) : state === "self" || state === "expired" ? (
                              <span className="inline-flex items-center gap-1 text-small text-text-tertiary">
                                <Lock size={12} aria-hidden="true" />
                                {state === "self" ? t("pages.staff.selfRequest") : t("pages.staff.expired")}
                              </span>
                            ) : null}
                          </td>
                        ) : null}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </DenseTableFrame>
          )}
        </>
      )}
      {confirmItem === null ? <MutationErrorNote error={approve.error} /> : null}
      <ReasonConfirmDialog
        open={confirmItem !== null}
        onOpenChange={(next) => {
          if (!next) setConfirmItem(null);
        }}
        title={t("pages.staff.approve")}
        target={confirmItem?.targetUserId ?? ""}
        consequence={t("pages.staff.approveConsequence")}
        confirmLabel={t("pages.staff.approve")}
        requireReason={false}
        destructive
        pending={approve.isPending}
        error={approve.error}
        onConfirm={() => {
          if (confirmItem === null) return;
          approve.mutate(confirmItem.id, {
            onSuccess: () => {
              setConfirmItem(null);
            },
          });
        }}
      />
    </DensePanel>
  );
}
