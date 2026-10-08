import { useState } from "react";
import { useTranslation } from "react-i18next";
import { isApiError } from "@/shared/api";
import { Badge, Button, Dialog, DialogContent, DialogDescription, DialogTitle, Input } from "@/shared/ui";
import {
  AdminPageHeader,
  AdminSection,
  AdminTableScroll,
  ApiErrorNote,
  SavedNote,
  adminTableClass,
  adminTdClass,
  adminThClass,
  emptyProductForm,
  formatProductVnd as formatVnd,
  formToPayload,
  productToForm,
  useAdminProducts,
  useCreateAdminProduct,
  useSetAdminProductStatus,
  useUpdateAdminProduct,
  validateProductForm,
} from "@/features/admin";
import type { AdminProduct, AdminProductStatus, ProductFormValues } from "@/features/admin";

const STATUS_TONE: Record<AdminProductStatus, "neutral" | "success" | "warning"> = {
  DRAFT: "warning",
  PUBLISHED: "success",
  ARCHIVED: "neutral",
};

const textareaClass =
  "min-h-28 w-full rounded-xl border border-border bg-surface px-3 py-2 text-body text-text-primary focus:outline-none focus-visible:ring-2 focus-visible:ring-primary";

export function AdminProductsPage() {
  const { t } = useTranslation("admin");
  const products = useAdminProducts();
  const create = useCreateAdminProduct();
  const update = useUpdateAdminProduct();
  const setStatus = useSetAdminProductStatus();
  // null = đóng; "new" = tạo; còn lại = id đang sửa.
  const [editing, setEditing] = useState<string | null>(null);
  const [form, setForm] = useState<ProductFormValues>(emptyProductForm);
  const [showErrors, setShowErrors] = useState(false);
  const [saved, setSaved] = useState(false);
  const [archiving, setArchiving] = useState<AdminProduct | null>(null);

  const errors = validateProductForm(form);
  const isEditingExisting = editing !== null && editing !== "new";
  const mutation = isEditingExisting ? update : create;
  const duplicateSku = !isEditingExisting && isApiError(create.error) && create.error.status === 409;

  function openForm(product: AdminProduct | null) {
    setEditing(product ? product.id : "new");
    setForm(product ? productToForm(product) : emptyProductForm());
    setShowErrors(false);
    setSaved(false);
    create.reset();
    update.reset();
  }

  function closeForm() {
    setEditing(null);
  }

  function submit() {
    setShowErrors(true);
    if (Object.keys(errors).length > 0 || editing === null) return;
    const payload = formToPayload(form);
    const done = {
      onSuccess: () => {
        setSaved(true);
        setEditing(null);
      },
    };
    if (isEditingExisting) update.mutate({ id: editing, payload }, done);
    else create.mutate(payload, done);
  }

  function changeStatus(product: AdminProduct, status: AdminProductStatus) {
    setSaved(false);
    setStatus.mutate(
      { id: product.id, status },
      {
        onSuccess: () => {
          setSaved(true);
        },
        onSettled: () => {
          setArchiving(null);
        },
      },
    );
  }

  const set = <K extends keyof ProductFormValues>(key: K, value: ProductFormValues[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };
  const err = (key: keyof ProductFormValues) =>
    showErrors && errors[key] ? t(`productAdmin.errors.${key as "sku"}`) : undefined;

  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader
        title={t("pages.products.title")}
        description={t("pages.products.description")}
        specRef="GET/POST/PUT/PATCH /api/v1/admin/shop/products"
      />

      <AdminSection title={t("productAdmin.listTitle")} description={t("productAdmin.listDescription")}>
        <div className="flex items-center gap-3">
          <Button
            variant="primary"
            size="sm"
            onClick={() => {
              openForm(null);
            }}
          >
            {t("productAdmin.add")}
          </Button>
          {editing === null ? <SavedNote visible={saved} /> : null}
        </div>
        {products.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {products.isError ? <ApiErrorNote error={products.error} /> : null}
        {setStatus.isError ? <ApiErrorNote error={setStatus.error} /> : null}
        {products.data && products.data.length === 0 ? (
          <p className="text-caption text-text-secondary">{t("feedback.empty")}</p>
        ) : null}
        {products.data && products.data.length > 0 ? (
          <AdminTableScroll>
            <table className={adminTableClass}>
              <thead>
                <tr>
                  <th className={adminThClass}>{t("productAdmin.image")}</th>
                  <th className={adminThClass}>{t("productAdmin.name")}</th>
                  <th className={adminThClass}>{t("productAdmin.sku")}</th>
                  <th className={adminThClass}>{t("productAdmin.price")}</th>
                  <th className={adminThClass}>{t("productAdmin.stock")}</th>
                  <th className={adminThClass}>{t("productAdmin.status")}</th>
                  <th className={adminThClass}>{t("productAdmin.action")}</th>
                </tr>
              </thead>
              <tbody>
                {products.data.map((product) => (
                  <tr key={product.id}>
                    <td className={adminTdClass}>
                      {product.imageUrl ? (
                        <img src={product.imageUrl} alt="" loading="lazy" className="size-10 rounded-lg object-cover" />
                      ) : null}
                    </td>
                    <td className={adminTdClass}>{product.name}</td>
                    <td className={adminTdClass}>
                      <code>{product.sku}</code>
                    </td>
                    <td className={adminTdClass}>
                      {formatVnd(product.priceVnd)}
                      {product.compareAtPriceVnd !== null ? (
                        <span className="block text-small text-text-tertiary line-through">
                          {formatVnd(product.compareAtPriceVnd)}
                        </span>
                      ) : null}
                    </td>
                    <td className={adminTdClass}>{product.stockQuantity}</td>
                    <td className={adminTdClass}>
                      <Badge tone={STATUS_TONE[product.status]}>{t(`productAdmin.statuses.${product.status}`)}</Badge>
                    </td>
                    <td className={adminTdClass}>
                      <div className="flex flex-wrap gap-1">
                        <Button
                          variant="tertiary"
                          size="sm"
                          onClick={() => {
                            openForm(product);
                          }}
                        >
                          {t("actions.edit")}
                        </Button>
                        {product.status !== "PUBLISHED" ? (
                          <Button
                            variant="tertiary"
                            size="sm"
                            disabled={setStatus.isPending}
                            onClick={() => {
                              changeStatus(product, "PUBLISHED");
                            }}
                          >
                            {t("productAdmin.publish")}
                          </Button>
                        ) : null}
                        {product.status !== "ARCHIVED" ? (
                          <Button
                            variant="tertiary"
                            size="sm"
                            disabled={setStatus.isPending}
                            onClick={() => {
                              setArchiving(product);
                            }}
                          >
                            {t("productAdmin.archive")}
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableScroll>
        ) : null}
      </AdminSection>

      {editing !== null ? (
        <AdminSection
          title={isEditingExisting ? t("productAdmin.editTitle", { sku: form.sku }) : t("productAdmin.createTitle")}
        >
          <div className="grid gap-4 md:grid-cols-2">
            <Input
              label={t("productAdmin.sku")}
              value={form.sku}
              disabled={isEditingExisting}
              helperText={t("productAdmin.skuHelp")}
              error={duplicateSku ? t("productAdmin.duplicateSku") : err("sku")}
              onChange={(event) => {
                set("sku", event.target.value.toUpperCase());
              }}
            />
            <Input
              label={t("productAdmin.name")}
              value={form.name}
              maxLength={180}
              error={err("name")}
              onChange={(event) => {
                set("name", event.target.value);
              }}
            />
            <Input
              label={t("productAdmin.priceVnd")}
              type="number"
              min={0}
              step={1}
              value={form.priceVnd}
              error={err("priceVnd")}
              onChange={(event) => {
                set("priceVnd", event.target.value);
              }}
            />
            <Input
              label={t("productAdmin.compareAtPriceVnd")}
              type="number"
              min={0}
              step={1}
              value={form.compareAtPriceVnd}
              error={err("compareAtPriceVnd")}
              onChange={(event) => {
                set("compareAtPriceVnd", event.target.value);
              }}
            />
            <Input
              label={t("productAdmin.stockQuantity")}
              type="number"
              min={0}
              step={1}
              value={form.stockQuantity}
              error={err("stockQuantity")}
              onChange={(event) => {
                set("stockQuantity", event.target.value);
              }}
            />
            <label className="flex flex-col gap-1.5 text-caption font-semibold text-text-secondary">
              {t("productAdmin.status")}
              <select
                className="h-12 rounded-xl border border-border bg-surface px-3 text-body font-normal text-text-primary"
                value={form.status}
                onChange={(event) => {
                  set("status", event.target.value as AdminProductStatus);
                }}
              >
                {(["DRAFT", "PUBLISHED", "ARCHIVED"] as const).map((status) => (
                  <option key={status} value={status}>
                    {t(`productAdmin.statuses.${status}`)}
                  </option>
                ))}
              </select>
            </label>
            <div className="md:col-span-2">
              <Input
                label={t("productAdmin.imageUrl")}
                type="url"
                value={form.imageUrl}
                error={err("imageUrl")}
                onChange={(event) => {
                  set("imageUrl", event.target.value);
                }}
              />
            </div>
            <div className="flex flex-col gap-1.5 md:col-span-2">
              <label htmlFor="admin-product-description" className="text-caption font-semibold text-text-secondary">
                {t("productAdmin.description")}
              </label>
              <textarea
                id="admin-product-description"
                className={textareaClass}
                maxLength={4000}
                value={form.description}
                aria-invalid={err("description") !== undefined}
                onChange={(event) => {
                  set("description", event.target.value);
                }}
              />
              {err("description") ? <p className="text-caption text-danger-text">{err("description")}</p> : null}
            </div>
          </div>
          {duplicateSku ? null : <ApiErrorNote error={mutation.error} />}
          <div className="flex gap-2">
            <Button variant="primary" loading={mutation.isPending} onClick={submit}>
              {t("productAdmin.save")}
            </Button>
            <Button variant="tertiary" onClick={closeForm}>
              {t("actions.cancel")}
            </Button>
          </div>
        </AdminSection>
      ) : null}

      <Dialog
        open={archiving !== null}
        onOpenChange={(open) => {
          if (!open) setArchiving(null);
        }}
      >
        <DialogContent>
          <DialogTitle>{t("productAdmin.confirmArchiveTitle")}</DialogTitle>
          <DialogDescription>{t("productAdmin.confirmArchiveBody", { name: archiving?.name ?? "" })}</DialogDescription>
          <div className="mt-4 flex justify-end gap-2">
            <Button
              variant="tertiary"
              onClick={() => {
                setArchiving(null);
              }}
            >
              {t("actions.cancel")}
            </Button>
            <Button
              variant="primary"
              loading={setStatus.isPending}
              onClick={() => {
                if (archiving) changeStatus(archiving, "ARCHIVED");
              }}
            >
              {t("productAdmin.archive")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
