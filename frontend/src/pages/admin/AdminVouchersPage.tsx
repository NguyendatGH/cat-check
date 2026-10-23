import { useState, useId } from "react";
import { useTranslation } from "react-i18next";
import { Plus, Search, Ticket, Tag, Check, Trash2, Edit2, Power } from "lucide-react";
import { Badge, Button, Dialog, DialogContent, DialogDescription, DialogTitle, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminSection,
  ApiErrorNote,
  DenseTableFrame,
  SavedNote,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  formatProductVnd as formatVnd,
  useAdminPromos,
  useCreateAdminPromo,
  useUpdateAdminPromo,
  useSetAdminPromoStatus,
  useDeleteAdminPromo,
} from "@/features/admin";
import type { AdminPromoCode, AdminPromoCodePayload } from "@/features/admin";
import { useGroup2Can } from "./group2Access";

interface PromoFormState {
  code: string;
  label: string;
  type: "PERCENT" | "FIXED" | "SHIPPING";
  amount: string;
  minOrderVnd: string;
  maxDiscountVnd: string;
  usageLimit: string;
  startsAt: string;
  expiresAt: string;
  isActive: boolean;
}

const emptyPromoForm: PromoFormState = {
  code: "",
  label: "",
  type: "PERCENT",
  amount: "10",
  minOrderVnd: "0",
  maxDiscountVnd: "",
  usageLimit: "",
  startsAt: "",
  expiresAt: "",
  isActive: true,
};

function promoToForm(p: AdminPromoCode): PromoFormState {
  return {
    code: p.code,
    label: p.label,
    type: p.type,
    amount: String(p.amount),
    minOrderVnd: String(p.minOrderVnd),
    maxDiscountVnd: p.maxDiscountVnd !== null ? String(p.maxDiscountVnd) : "",
    usageLimit: p.usageLimit !== null ? String(p.usageLimit) : "",
    startsAt: p.startsAt ? p.startsAt.slice(0, 16) : "",
    expiresAt: p.expiresAt ? p.expiresAt.slice(0, 16) : "",
    isActive: p.isActive,
  };
}

export function AdminVouchersPage() {
  const { t } = useTranslation("admin");
  const promosQuery = useAdminPromos();
  const createMutation = useCreateAdminPromo();
  const updateMutation = useUpdateAdminPromo();
  const setStatusMutation = useSetAdminPromoStatus();
  const deleteMutation = useDeleteAdminPromo();

  const canEdit = useGroup2Can()("products");
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<string>("ALL");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");

  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<PromoFormState>(emptyPromoForm);
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});
  const [deletingPromo, setDeletingPromo] = useState<AdminPromoCode | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const formCodeId = useId();
  const formLabelId = useId();
  const formTypeId = useId();
  const formAmountId = useId();
  const formMinOrderId = useId();
  const formMaxDiscountId = useId();
  const formLimitId = useId();

  const promos = promosQuery.data ?? [];

  const filteredPromos = promos.filter((p) => {
    const matchSearch =
      search.trim() === "" ||
      p.code.toLowerCase().includes(search.toLowerCase()) ||
      p.label.toLowerCase().includes(search.toLowerCase());
    const matchType = typeFilter === "ALL" || p.type === typeFilter;
    const matchStatus =
      statusFilter === "ALL" ||
      (statusFilter === "ACTIVE" && p.isActive) ||
      (statusFilter === "INACTIVE" && !p.isActive);
    return matchSearch && matchType && matchStatus;
  });

  const totalPromos = promos.length;
  const activePromos = promos.filter((p) => p.isActive).length;
  const totalUses = promos.reduce((sum, p) => sum + p.usedCount, 0);

  function openCreate() {
    setEditingId("new");
    setForm(emptyPromoForm);
    setFormErrors({});
    createMutation.reset();
  }

  function openEdit(promo: AdminPromoCode) {
    setEditingId(promo.id);
    setForm(promoToForm(promo));
    setFormErrors({});
    updateMutation.reset();
  }

  function validate(f: PromoFormState): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!f.code.trim()) {
      errors.code = "Mã voucher không được để trống";
    } else if (!/^[A-Za-z0-9_-]{2,50}$/.test(f.code.trim())) {
      errors.code = "Mã voucher gồm 2-50 ký tự chữ, số, gạch nối";
    }
    if (!f.label.trim()) {
      errors.label = "Tên hiển thị không được để trống";
    }
    const amt = Number(f.amount);
    if (Number.isNaN(amt) || amt <= 0) {
      errors.amount = "Mức giảm phải lớn hơn 0";
    } else if (f.type === "PERCENT" && amt > 100) {
      errors.amount = "Phần trăm giảm tối đa là 100%";
    }
    const minOrd = Number(f.minOrderVnd);
    if (Number.isNaN(minOrd) || minOrd < 0) {
      errors.minOrderVnd = "Đơn tối thiểu không hợp lệ";
    }
    if (f.maxDiscountVnd) {
      const maxD = Number(f.maxDiscountVnd);
      if (Number.isNaN(maxD) || maxD <= 0) {
        errors.maxDiscountVnd = "Mức giảm tối đa phải lớn hơn 0";
      }
    }
    if (f.usageLimit) {
      const limit = Number(f.usageLimit);
      if (Number.isNaN(limit) || limit <= 0 || !Number.isInteger(limit)) {
        errors.usageLimit = "Giới hạn lượt dùng phải là số nguyên dương";
      }
    }
    return errors;
  }

  function handleSave() {
    const errs = validate(form);
    setFormErrors(errs);
    if (Object.keys(errs).length > 0) return;

    const payload: AdminPromoCodePayload = {
      code: form.code.trim().toUpperCase(),
      label: form.label.trim(),
      type: form.type,
      amount: Number(form.amount),
      minOrderVnd: Number(form.minOrderVnd) || 0,
      maxDiscountVnd: form.maxDiscountVnd ? Number(form.maxDiscountVnd) : null,
      usageLimit: form.usageLimit ? Number(form.usageLimit) : null,
      startsAt: form.startsAt ? new Date(form.startsAt).toISOString() : null,
      expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : null,
      isActive: form.isActive,
    };

    if (editingId === "new") {
      createMutation.mutate(payload, {
        onSuccess: (created) => {
          setEditingId(null);
          setSavedMessage(`Đã tạo mã ${created.code} thành công`);
        },
      });
    } else if (editingId) {
      updateMutation.mutate(
        { id: editingId, payload },
        {
          onSuccess: (updated) => {
            setEditingId(null);
            setSavedMessage(`Đã cập nhật mã ${updated.code} thành công`);
          },
        },
      );
    }
  }

  function handleToggleStatus(promo: AdminPromoCode) {
    if (!canEdit) return;
    setStatusMutation.mutate(
      { id: promo.id, isActive: !promo.isActive },
      {
        onSuccess: (updated) => {
          setSavedMessage(`Đã ${updated.isActive ? "kích hoạt" : "vô hiệu hóa"} mã ${updated.code}`);
        },
      },
    );
  }

  function handleDeleteConfirm() {
    if (!deletingPromo || !canEdit) return;
    deleteMutation.mutate(deletingPromo.id, {
      onSuccess: () => {
        setSavedMessage(`Đã xóa mã ${deletingPromo.code}`);
        setDeletingPromo(null);
      },
    });
  }

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("nav.vouchers", "Mã giảm giá")}
        description="Tạo và quản lý các mã voucher, khuyến mãi áp dụng cho giỏ hàng shop."
        actions={
          canEdit ? (
            <Button
              type="button"
              variant="primary"
              size="sm"
              onClick={openCreate}
              className="flex items-center gap-1.5"
            >
              <Plus size={16} aria-hidden="true" />
              <span>Tạo mã giảm giá</span>
            </Button>
          ) : undefined
        }
      />

      <SavedNote visible={Boolean(savedMessage)} />

      {/* KPI Stats */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="flex items-center gap-3.5 rounded-2xl border border-border bg-surface p-4 shadow-sm">
          <div className="flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <Ticket size={22} aria-hidden="true" />
          </div>
          <div>
            <p className="text-[12px] font-medium text-text-secondary">Tổng số mã</p>
            <p className="text-[22px] font-bold text-text-primary">{totalPromos}</p>
          </div>
        </div>
        <div className="flex items-center gap-3.5 rounded-2xl border border-border bg-surface p-4 shadow-sm">
          <div className="flex size-11 items-center justify-center rounded-xl bg-success-bg text-success-text">
            <Check size={22} aria-hidden="true" />
          </div>
          <div>
            <p className="text-[12px] font-medium text-text-secondary">Đang hoạt động</p>
            <p className="text-[22px] font-bold text-text-primary">{activePromos}</p>
          </div>
        </div>
        <div className="flex items-center gap-3.5 rounded-2xl border border-border bg-surface p-4 shadow-sm">
          <div className="flex size-11 items-center justify-center rounded-xl bg-info/20 text-primary-dark">
            <Tag size={22} aria-hidden="true" />
          </div>
          <div>
            <p className="text-[12px] font-medium text-text-secondary">Tổng lượt áp dụng</p>
            <p className="text-[22px] font-bold text-text-primary">{totalUses}</p>
          </div>
        </div>
      </div>

      {/* Table Section */}
      <AdminSection
        title="Danh sách mã giảm giá"
        description="Mã đang kích hoạt sẽ tự động hiển thị gợi ý tại màn hình thanh toán của khách hàng."
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="relative min-w-64 max-w-sm flex-1">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-tertiary" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Tìm theo mã hoặc tên voucher..."
              className="w-full rounded-xl border border-border bg-background py-1.5 pl-9 pr-3 text-[13px] text-text-primary placeholder:text-text-tertiary focus:border-primary focus:outline-none"
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              aria-label="Lọc theo loại voucher"
              className="rounded-xl border border-border bg-background px-3 py-1.5 text-[13px] font-medium text-text-primary focus:border-primary focus:outline-none"
            >
              <option value="ALL">Tất cả loại</option>
              <option value="PERCENT">Phần trăm (%)</option>
              <option value="SHIPPING">Phí vận chuyển</option>
              <option value="FIXED">Số tiền cố định</option>
            </select>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              aria-label="Lọc theo trạng thái"
              className="rounded-xl border border-border bg-background px-3 py-1.5 text-[13px] font-medium text-text-primary focus:border-primary focus:outline-none"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="ACTIVE">Đang bật</option>
              <option value="INACTIVE">Đã tắt</option>
            </select>
          </div>
        </div>

        <DenseTableFrame label="Danh sách mã giảm giá">
          <table className={denseTableClass}>
            <thead>
              <tr>
                <th className={denseThClass}>Mã Code</th>
                <th className={denseThClass}>Tên hiển thị</th>
                <th className={denseThClass}>Mức giảm</th>
                <th className={denseThClass}>Đơn tối thiểu</th>
                <th className={denseThClass}>Lượt dùng</th>
                <th className={denseThClass}>Hiệu lực</th>
                <th className={denseThClass}>Trạng thái</th>
                <th className={denseThClass}>Hành động</th>
              </tr>
            </thead>
            <tbody>
              {filteredPromos.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-[13px] text-text-tertiary">
                    Không có mã giảm giá nào phù hợp.
                  </td>
                </tr>
              ) : (
                filteredPromos.map((p) => {
                  let discountDesc = "";
                  if (p.type === "PERCENT") {
                    discountDesc = `${p.amount}%${p.maxDiscountVnd ? ` (tối đa ${formatVnd(p.maxDiscountVnd)})` : ""}`;
                  } else if (p.type === "SHIPPING") {
                    discountDesc = `Giảm ship ${formatVnd(p.amount)}`;
                  } else {
                    discountDesc = `Giảm ${formatVnd(p.amount)}`;
                  }

                  const usageText = `${p.usedCount}${p.usageLimit ? ` / ${p.usageLimit}` : " / ∞"}`;
                  const expiryText = p.expiresAt
                    ? `Đến ${formatAdminDateTime(p.expiresAt)}`
                    : p.startsAt
                      ? `Từ ${formatAdminDateTime(p.startsAt)}`
                      : "Vô thời hạn";

                  return (
                    <tr key={p.id} className={denseRowClass}>
                      <td className={denseTdClass}>
                        <span className="font-mono font-bold text-[13px] text-primary-dark">{p.code}</span>
                      </td>
                      <td className={denseTdClass}>
                        <span className="font-medium text-text-primary">{p.label}</span>
                      </td>
                      <td className={denseTdClass}>
                        <span className="font-semibold text-success-text">{discountDesc}</span>
                      </td>
                      <td className={denseTdClass}>
                        <span className="text-text-secondary">
                          {p.minOrderVnd > 0 ? formatVnd(p.minOrderVnd) : "0đ"}
                        </span>
                      </td>
                      <td className={denseTdClass}>
                        <span className="font-mono text-[12px] text-text-secondary">{usageText}</span>
                      </td>
                      <td className={denseTdClass}>
                        <span className="text-[12px] text-text-secondary">{expiryText}</span>
                      </td>
                      <td className={denseTdClass}>
                        <Badge tone={p.isActive ? "success" : "neutral"}>
                          {p.isActive ? "Đang bật" : "Đã tắt"}
                        </Badge>
                      </td>
                      <td className={denseTdClass}>
                        <div className="flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => openEdit(p)}
                            disabled={!canEdit}
                            title="Sửa voucher"
                            className="flex size-7 items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-black/5 hover:text-text-primary active:scale-95 disabled:opacity-40"
                          >
                            <Edit2 size={14} aria-hidden="true" />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleToggleStatus(p)}
                            disabled={!canEdit || setStatusMutation.isPending}
                            title={p.isActive ? "Tắt mã" : "Bật mã"}
                            className={`flex size-7 items-center justify-center rounded-lg transition-colors active:scale-95 disabled:opacity-40 ${
                              p.isActive
                                ? "text-success-text hover:bg-success-bg"
                                : "text-text-tertiary hover:bg-black/5"
                            }`}
                          >
                            <Power size={14} aria-hidden="true" />
                          </button>
                          <button
                            type="button"
                            onClick={() => setDeletingPromo(p)}
                            disabled={!canEdit}
                            title="Xóa voucher"
                            className="flex size-7 items-center justify-center rounded-lg text-danger-text transition-colors hover:bg-danger-bg active:scale-95 disabled:opacity-40"
                          >
                            <Trash2 size={14} aria-hidden="true" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </DenseTableFrame>
      </AdminSection>

      {/* Create / Edit Modal Dialog */}
      <Dialog open={editingId !== null} onOpenChange={(open) => !open && setEditingId(null)}>
        <DialogContent className="max-w-md">
          <DialogTitle>{editingId === "new" ? "Tạo mã giảm giá mới" : `Sửa mã ${form.code}`}</DialogTitle>
          <DialogDescription>
            Điền các thông tin giảm giá, điều kiện tối thiểu và thời hạn hiệu lực cho mã.
          </DialogDescription>

          <div className="mt-3 flex flex-col gap-3 text-[13px]">
            <div>
              <label htmlFor={formCodeId} className="block font-semibold text-text-primary mb-1">
                Mã Code <span className="text-danger-text">*</span>
              </label>
              <Input
                id={formCodeId}
                value={form.code}
                disabled={editingId !== "new"}
                onChange={(e) => setForm((prev) => ({ ...prev, code: e.target.value.toUpperCase() }))}
                placeholder="Ví dụ: SUMMER2026"
                className="font-mono font-bold uppercase"
              />
              {formErrors.code ? <p className="mt-1 text-[11px] text-danger-text">{formErrors.code}</p> : null}
            </div>

            <div>
              <label htmlFor={formLabelId} className="block font-semibold text-text-primary mb-1">
                Tên hiển thị <span className="text-danger-text">*</span>
              </label>
              <Input
                id={formLabelId}
                value={form.label}
                onChange={(e) => setForm((prev) => ({ ...prev, label: e.target.value }))}
                placeholder="Ví dụ: Giảm 15% mùa hè"
              />
              {formErrors.label ? <p className="mt-1 text-[11px] text-danger-text">{formErrors.label}</p> : null}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label htmlFor={formTypeId} className="block font-semibold text-text-primary mb-1">
                  Loại giảm giá
                </label>
                <select
                  id={formTypeId}
                  value={form.type}
                  onChange={(e) =>
                    setForm((prev) => ({
                      ...prev,
                      type: e.target.value as "PERCENT" | "FIXED" | "SHIPPING",
                      amount: e.target.value === "PERCENT" ? "10" : "30000",
                    }))
                  }
                  className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-[13px] text-text-primary focus:border-primary focus:outline-none"
                >
                  <option value="PERCENT">Phần trăm (%)</option>
                  <option value="SHIPPING">Tiền ship (VND)</option>
                  <option value="FIXED">Cố định (VND)</option>
                </select>
              </div>

              <div>
                <label htmlFor={formAmountId} className="block font-semibold text-text-primary mb-1">
                  Mức giảm ({form.type === "PERCENT" ? "%" : "đ"}) <span className="text-danger-text">*</span>
                </label>
                <Input
                  id={formAmountId}
                  type="number"
                  value={form.amount}
                  onChange={(e) => setForm((prev) => ({ ...prev, amount: e.target.value }))}
                  placeholder={form.type === "PERCENT" ? "10" : "30000"}
                />
                {formErrors.amount ? <p className="mt-1 text-[11px] text-danger-text">{formErrors.amount}</p> : null}
              </div>
            </div>

            {form.type === "PERCENT" ? (
              <div>
                <label htmlFor={formMaxDiscountId} className="block font-semibold text-text-primary mb-1">
                  Giảm tối đa (VND, tuỳ chọn)
                </label>
                <Input
                  id={formMaxDiscountId}
                  type="number"
                  value={form.maxDiscountVnd}
                  onChange={(e) => setForm((prev) => ({ ...prev, maxDiscountVnd: e.target.value }))}
                  placeholder="Để trống nếu không giới hạn trần"
                />
                {formErrors.maxDiscountVnd ? (
                  <p className="mt-1 text-[11px] text-danger-text">{formErrors.maxDiscountVnd}</p>
                ) : null}
              </div>
            ) : null}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label htmlFor={formMinOrderId} className="block font-semibold text-text-primary mb-1">
                  Đơn tối thiểu (VND)
                </label>
                <Input
                  id={formMinOrderId}
                  type="number"
                  value={form.minOrderVnd}
                  onChange={(e) => setForm((prev) => ({ ...prev, minOrderVnd: e.target.value }))}
                  placeholder="0"
                />
                {formErrors.minOrderVnd ? (
                  <p className="mt-1 text-[11px] text-danger-text">{formErrors.minOrderVnd}</p>
                ) : null}
              </div>

              <div>
                <label htmlFor={formLimitId} className="block font-semibold text-text-primary mb-1">
                  Giới hạn lượt dùng
                </label>
                <Input
                  id={formLimitId}
                  type="number"
                  value={form.usageLimit}
                  onChange={(e) => setForm((prev) => ({ ...prev, usageLimit: e.target.value }))}
                  placeholder="Để trống = vô hạn"
                />
                {formErrors.usageLimit ? (
                  <p className="mt-1 text-[11px] text-danger-text">{formErrors.usageLimit}</p>
                ) : null}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block font-semibold text-text-primary mb-1">Bắt đầu từ</label>
                <input
                  type="datetime-local"
                  value={form.startsAt}
                  onChange={(e) => setForm((prev) => ({ ...prev, startsAt: e.target.value }))}
                  className="w-full rounded-xl border border-border bg-surface px-2.5 py-1.5 text-[12px] text-text-primary focus:border-primary focus:outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-text-primary mb-1">Hết hạn vào</label>
                <input
                  type="datetime-local"
                  value={form.expiresAt}
                  onChange={(e) => setForm((prev) => ({ ...prev, expiresAt: e.target.value }))}
                  className="w-full rounded-xl border border-border bg-surface px-2.5 py-1.5 text-[12px] text-text-primary focus:border-primary focus:outline-none"
                />
              </div>
            </div>

            <label className="flex items-center gap-2 pt-1 font-semibold text-text-primary cursor-pointer">
              <input
                type="checkbox"
                checked={form.isActive}
                onChange={(e) => setForm((prev) => ({ ...prev, isActive: e.target.checked }))}
                className="size-4 rounded accent-primary"
              />
              <span>Kích hoạt mã ngay</span>
            </label>

            {createMutation.isError ? <ApiErrorNote error={createMutation.error} /> : null}
            {updateMutation.isError ? <ApiErrorNote error={updateMutation.error} /> : null}

            <div className="mt-3 flex justify-end gap-2 border-t border-border pt-3">
              <Button type="button" variant="tertiary" size="sm" onClick={() => setEditingId(null)}>
                Hủy
              </Button>
              <Button
                type="button"
                variant="primary"
                size="sm"
                onClick={handleSave}
                disabled={createMutation.isPending || updateMutation.isPending}
              >
                {createMutation.isPending || updateMutation.isPending ? "Đang lưu..." : "Lưu mã giảm giá"}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deletingPromo !== null} onOpenChange={(open) => !open && setDeletingPromo(null)}>
        <DialogContent className="max-w-sm">
          <DialogTitle>Xóa mã giảm giá</DialogTitle>
          <DialogDescription>
            Bạn có chắc chắn muốn xóa mã <span className="font-mono font-bold">{deletingPromo?.code}</span>? Hành động
            này không thể hoàn tác.
          </DialogDescription>
          {deleteMutation.isError ? <ApiErrorNote error={deleteMutation.error} /> : null}
          <div className="mt-4 flex justify-end gap-2">
            <Button type="button" variant="tertiary" size="sm" onClick={() => setDeletingPromo(null)}>
              Hủy
            </Button>
            <Button
              type="button"
              variant="primary"
              size="sm"
              className="bg-danger hover:bg-danger/90 text-white"
              onClick={handleDeleteConfirm}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? "Đang xóa..." : "Xóa vĩnh viễn"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
