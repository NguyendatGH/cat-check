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
  ReasonField,
  SearchField,
  SortableTh,
  TableSkeleton,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  isReasonValid,
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

/** Vai trò cấp được qua API; backend từ chối ADMIN_SUPER, DPO, USER (`ROLE_NOT_ASSIGNABLE`). */
const STAFF_ROLES = ["ADMIN_SUPPORT", "ADMIN_CATALOG", "MODERATOR", "VET"] as const;

/**
 * Khoá `ROLES` (shared/auth/roles.ts) mà trang này hỏi: `staffRolesWrite` (chỉ ADMIN_SUPER),
 * `totpResetRequestsRead` (ADMIN_SUPER, DPO), `totpResetApprove` (chỉ DPO).
 *
 * `AdminLayout` tính khả năng cho MỌI khoá của `ROLES` rồi đặt vào `AdminAccessProvider`, nên
 * ba khoá này có sẵn lúc chạy; kiểu `AdminCapabilityKey` chưa liệt kê chúng nên nới kiểu ở đây.
 * Khoá vắng ⇒ `false` (không có provider thì không hiện nút ghi nào). Backend vẫn kiểm độc lập.
 */
type StaffCapability = "staffRolesWrite" | "totpResetRequestsRead" | "totpResetApprove";

function useStaffCan(): (key: StaffCapability) => boolean {
  const can = useAdminCan();
  return (key) => can(key);
}

type ApprovalState = "allowed" | "self" | "expired" | "closed";

/**
 * Quy tắc hai người (backend `approveTotpReset`): người tạo yêu cầu không tự duyệt
 * (`MFA_RESET_TWO_PERSON_REQUIRED`); yêu cầu hết hạn hoặc không còn PENDING bị từ chối
 * (`MFA_RESET_NOT_PENDING`). Chặn sẵn ở UI khi response có đủ dữ liệu.
 */
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

/**
 * `/admin/staff` — L13–L17: cấp/gỡ role staff, tạo yêu cầu reset TOTP (chỉ ADMIN_SUPER), xem
 * hàng đợi (ADMIN_SUPER, DPO) và duyệt reset TOTP theo quy tắc hai người (chỉ DPO).
 */
export function AdminStaffPage() {
  const { t } = useTranslation("admin");
  const can = useStaffCan();
  const canWriteRoles = can("staffRolesWrite");
  const canReadQueue = can("totpResetRequestsRead");
  const canApprove = can("totpResetApprove");

  const header = (
    <AdminPageHeader
      title={t("pages.staff.title")}
      description={t("pages.staff.description")}
      specRef="L13–L17 · /api/v1/admin"
    />
  );

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

/** Cấp/gỡ role và tạo yêu cầu reset TOTP — chỉ dựng khi có `staffRolesWrite`. */
function StaffRolePanel() {
  const { t } = useTranslation("admin");
  const [userId, setUserId] = useState("");
  const [role, setRole] = useState<string>(STAFF_ROLES[0]);
  const [reason, setReason] = useState("");
  const grant = useGrantAdminRole();
  const revoke = useRevokeAdminRole();
  const requestReset = useRequestAdminMfaReset();
  const canSubmit = userId.trim().length > 0 && isReasonValid(reason);

  return (
    <DensePanel title={t("pages.staff.roleTitle")} description={t("pages.staff.roleDescription")}>
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_220px] md:items-end">
        <Input
          label={t("pages.staff.userId")}
          className="h-10"
          value={userId}
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
              {item}
            </option>
          ))}
        </AdminSelect>
      </div>
      <ReasonField value={reason} onChange={setReason} showError={reason.length > 0} />
      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="secondary"
          size="sm"
          disabled={!canSubmit || grant.isPending}
          onClick={() => {
            grant.mutate({ userId: userId.trim(), role, reason: reason.trim() });
          }}
        >
          {t("pages.staff.grant")}
        </Button>
        <Button
          variant="tertiary"
          size="sm"
          disabled={!canSubmit || revoke.isPending}
          onClick={() => {
            revoke.mutate({ userId: userId.trim(), role });
          }}
        >
          {t("pages.staff.revoke")}
        </Button>
        <span className="mx-1 hidden h-6 w-px bg-border sm:block" aria-hidden="true" />
        <Button
          variant="primary"
          size="sm"
          disabled={!canSubmit || requestReset.isPending}
          onClick={() => {
            requestReset.mutate({ userId: userId.trim(), reason: reason.trim() });
          }}
        >
          {t("pages.staff.requestReset")}
        </Button>
      </div>
      <MutationErrorNote error={grant.error ?? revoke.error ?? requestReset.error} />
    </DensePanel>
  );
}

/** Hàng đợi reset TOTP — chỉ dựng khi có `totpResetRequestsRead` để không gọi API sẽ trả 403. */
function ResetQueuePanel({ canApprove }: { canApprove: boolean }) {
  const { t } = useTranslation("admin");
  const requests = useAdminMfaResetRequests();
  const approve = useApproveAdminMfaReset();
  const currentUserId = useSessionStore((state) => state.user?.id);
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortState<QueueSortKey> | null>({ key: "requestedAt", direction: "asc" });

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
                                  approve.mutate(item.id);
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
      <MutationErrorNote error={approve.error} />
    </DensePanel>
  );
}
