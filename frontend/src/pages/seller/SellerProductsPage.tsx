import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ArrowDown, ArrowUp, ArrowUpDown, Package, Plus, Search } from "lucide-react";
import { isApiError } from "@/shared/api";
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Input,
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
  SkeletonLoader,
  Tabs,
  TabsList,
  TabsTrigger,
} from "@/shared/ui";
import {
  adminProductEtag,
  emptyProductForm,
  formatProductVnd,
  formToPayload,
  productToForm,
  useAdminProducts,
  useCreateAdminProduct,
  useSetAdminProductStatus,
  useUpdateAdminProduct,
  validateProductForm,
} from "@/features/admin";
import type { AdminProduct, AdminProductStatus, ProductFormValues } from "@/features/admin";
import {
  filterProducts,
  planBulkStatus,
  runBulk,
  sortProducts,
  type SortDir,
  type SortKey,
  type StatusFilter,
} from "@/features/seller";

const STATUS_TONE: Record<AdminProductStatus, "neutral" | "success" | "warning"> = {
  DRAFT: "warning",
  PUBLISHED: "success",
  ARCHIVED: "neutral",
};

const FILTERS: readonly StatusFilter[] = ["ALL", "PUBLISHED", "DRAFT", "ARCHIVED"];

const controlClass =
  "w-full rounded-xl border border-border bg-surface px-3 text-body text-text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]";

const thClass =
  "sticky top-0 z-10 bg-background-alt px-3 py-2 text-left text-caption font-semibold whitespace-nowrap text-text-secondary";
const tdClass = "h-10 px-3 py-1.5 text-caption text-text-primary";

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" });
}

export function SellerProductsPage() {
  const { t } = useTranslation("seller");
  const products = useAdminProducts();
  const create = useCreateAdminProduct();
  const update = useUpdateAdminProduct();
  const setStatus = useSetAdminProductStatus();

  const [filter, setFilter] = useState<StatusFilter>("ALL");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<{ key: SortKey; dir: SortDir }>({ key: "updatedAt", dir: "desc" });
  const [selected, setSelected] = useState<string[]>([]);
  const [bulkMsg, setBulkMsg] = useState<{ kind: "progress" | "done" | "error"; text: string } | null>(null);
  const [bulkRunning, setBulkRunning] = useState(false);

  // null = đóng; "new" = tạo; còn lại = id đang sửa.
  const [editing, setEditing] = useState<string | null>(null);
  // If-Match chốt lúc MỞ form (bản người dùng đang xem), không lấy từ lần refetch sau.
  const [editingEtag, setEditingEtag] = useState<string | null>(null);
  const [form, setForm] = useState<ProductFormValues>(emptyProductForm);
  const [showErrors, setShowErrors] = useState(false);
  const [saved, setSaved] = useState(false);

  const errors = validateProductForm(form);
  const isEditingExisting = editing !== null && editing !== "new";
  const mutation = isEditingExisting ? update : create;
  const mutationError = mutation.error;
  const duplicateSku = !isEditingExisting && isApiError(create.error) && create.error.status === 409;
  const conflict = isEditingExisting && isApiError(update.error) && update.error.status === 412;

  const visible = useMemo(
    () => sortProducts(filterProducts(products.data ?? [], filter, query), sort.key, sort.dir),
    [products.data, filter, query, sort],
  );
  const visibleIds = visible.map((p) => p.id);
  const selectedVisible = selected.filter((id) => visibleIds.includes(id));
  const allSelected = visible.length > 0 && selectedVisible.length === visible.length;

  function openForm(product: AdminProduct | null) {
    setEditing(product ? product.id : "new");
    setEditingEtag(product ? adminProductEtag(product) : null);
    setForm(product ? productToForm(product) : emptyProductForm());
    setShowErrors(false);
    setSaved(false);
    create.reset();
    update.reset();
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
    if (isEditingExisting) {
      if (editingEtag === null) return;
      update.mutate({ id: editing, payload, etag: editingEtag }, done);
    } else create.mutate(payload, done);
  }

  function reloadLatest() {
    void products.refetch().then((result) => {
      const fresh = result.data?.find((p) => p.id === editing);
      if (fresh) openForm(fresh);
      else setEditing(null);
    });
  }

  function toggleSort(key: SortKey) {
    setSort((s) => (s.key === key ? { key, dir: s.dir === "asc" ? "desc" : "asc" } : { key, dir: "asc" }));
  }

  function toggleRow(id: string) {
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  }

  async function runBulkStatus(target: AdminProductStatus) {
    const steps = planBulkStatus(products.data ?? [], selectedVisible, target);
    if (steps.length === 0) {
      setBulkMsg({ kind: "done", text: t("products.bulk.nothing") });
      return;
    }
    setBulkRunning(true);
    const result = await runBulk(
      steps,
      (step) => setStatus.mutateAsync(step),
      (done, total) => {
        setBulkMsg({ kind: "progress", text: t("products.bulk.progress", { done, total }) });
      },
    );
    setBulkRunning(false);
    if (result.failed) {
      const name = products.data?.find((p) => p.id === result.failed?.step.id)?.name ?? "";
      setBulkMsg({
        kind: "error",
        text: t("products.bulk.failed", { name, done: result.done, total: result.total }),
      });
    } else {
      setBulkMsg({ kind: "done", text: t("products.bulk.done", { done: result.done, total: result.total }) });
      setSelected([]);
    }
  }

  function quickStatus(product: AdminProduct, status: AdminProductStatus) {
    setBulkMsg(null);
    setStatus.mutate({ id: product.id, status });
  }

  const set = <K extends keyof ProductFormValues>(key: K, value: ProductFormValues[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };
  const err = (key: keyof ProductFormValues) =>
    showErrors && errors[key] ? t(`products.form.errors.${key as "sku"}`) : undefined;

  function SortHeader({ k, label }: { k: SortKey; label: string }) {
    const active = sort.key === k;
    const Icon = !active ? ArrowUpDown : sort.dir === "asc" ? ArrowUp : ArrowDown;
    return (
      <th
        className={thClass}
        scope="col"
        aria-sort={active ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}
      >
        <button
          type="button"
          onClick={() => {
            toggleSort(k);
          }}
          aria-label={t("products.sortBy", { column: label })}
          className="inline-flex items-center gap-1 rounded font-semibold focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-[var(--focus-ring-color)]"
        >
          {label}
          <Icon className="size-3.5" aria-hidden="true" />
        </button>
      </th>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-h2 text-text-primary">{t("products.title")}</h1>
        <Button
          variant="primary"
          size="sm"
          onClick={() => {
            openForm(null);
          }}
        >
          <Plus className="size-4" aria-hidden="true" />
          {t("products.add")}
        </Button>
      </header>

      <div className="flex flex-wrap items-end justify-between gap-3">
        <Tabs
          value={filter}
          onValueChange={(v) => {
            setFilter(v as StatusFilter);
          }}
        >
          <TabsList className="max-w-full overflow-x-auto">
            {FILTERS.map((f) => (
              <TabsTrigger key={f} value={f}>
                {t(`products.tabs.${f}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        <div className="w-full sm:w-72">
          <Input
            label={t("products.search")}
            type="search"
            value={query}
            leftIcon={<Search className="size-4" />}
            onChange={(event) => {
              setQuery(event.target.value);
            }}
          />
        </div>
      </div>

      <div role="status" aria-live="polite" className="text-caption text-text-secondary empty:hidden">
        {saved && editing === null ? t("products.form.saved") : null}
        {bulkMsg?.kind !== "error" ? bulkMsg?.text : null}
      </div>
      {bulkMsg?.kind === "error" ? (
        <p role="alert" className="rounded-xl bg-danger-bg px-3 py-2 text-caption text-danger-text">
          {bulkMsg.text}
        </p>
      ) : null}
      {setStatus.isError && !bulkRunning && bulkMsg?.kind !== "error" ? (
        <p role="alert" className="rounded-xl bg-danger-bg px-3 py-2 text-caption text-danger-text">
          {t("products.statusError")}
        </p>
      ) : null}

      {selectedVisible.length > 0 ? (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-border bg-info px-3 py-2">
          <span className="text-caption font-semibold text-text-primary">
            {t("products.bulk.selected", { count: selectedVisible.length })}
          </span>
          <Button
            size="sm"
            variant="primary"
            disabled={bulkRunning}
            onClick={() => {
              void runBulkStatus("PUBLISHED");
            }}
          >
            {t("products.bulk.publish")}
          </Button>
          <Button
            size="sm"
            variant="secondary"
            disabled={bulkRunning}
            onClick={() => {
              void runBulkStatus("ARCHIVED");
            }}
          >
            {t("products.bulk.unpublish")}
          </Button>
          <Button
            size="sm"
            variant="tertiary"
            disabled={bulkRunning}
            onClick={() => {
              setSelected([]);
            }}
          >
            {t("products.bulk.clear")}
          </Button>
        </div>
      ) : null}

      {products.isPending ? (
        <div className="flex flex-col gap-2" role="status" aria-label="Đang tải">
          {[0, 1, 2, 3, 4].map((i) => (
            <SkeletonLoader key={i} className="h-10" />
          ))}
        </div>
      ) : null}
      {products.isError ? (
        <ErrorState
          title={t("products.loadError")}
          onRetry={() => {
            void products.refetch();
          }}
        />
      ) : null}
      {products.data && products.data.length === 0 ? (
        <EmptyState
          icon={<Package className="size-6" />}
          title={t("products.empty")}
          description={t("products.emptyDescription")}
        />
      ) : null}
      {products.data && products.data.length > 0 && visible.length === 0 ? (
        <EmptyState icon={<Search className="size-6" />} title={t("products.noMatch")} />
      ) : null}

      {visible.length > 0 ? (
        <div className="max-h-[70vh] overflow-auto rounded-xl border border-border bg-surface">
          <table className="w-full min-w-[820px] border-collapse">
            <thead>
              <tr>
                <th className={thClass} scope="col">
                  <input
                    type="checkbox"
                    className="size-4"
                    aria-label={t("products.selectAll")}
                    checked={allSelected}
                    onChange={() => {
                      setSelected(allSelected ? [] : visibleIds);
                    }}
                  />
                </th>
                <SortHeader k="name" label={t("products.columns.name")} />
                <SortHeader k="sku" label={t("products.columns.sku")} />
                <SortHeader k="priceVnd" label={t("products.columns.price")} />
                <SortHeader k="stockQuantity" label={t("products.columns.stock")} />
                <th className={thClass} scope="col">
                  {t("products.columns.status")}
                </th>
                <SortHeader k="updatedAt" label={t("products.columns.updatedAt")} />
                <th className={thClass} scope="col">
                  {t("products.columns.actions")}
                </th>
              </tr>
            </thead>
            <tbody>
              {visible.map((product) => (
                <tr key={product.id} className="border-t border-border">
                  <td className={tdClass}>
                    <input
                      type="checkbox"
                      className="size-4"
                      aria-label={t("products.selectRow", { name: product.name })}
                      checked={selected.includes(product.id)}
                      onChange={() => {
                        toggleRow(product.id);
                      }}
                    />
                  </td>
                  <td className={tdClass}>{product.name}</td>
                  <td className={tdClass}>
                    <code>{product.sku}</code>
                  </td>
                  <td className={tdClass}>{formatProductVnd(product.priceVnd)}</td>
                  <td className={tdClass}>{product.stockQuantity}</td>
                  <td className={tdClass}>
                    <Badge tone={STATUS_TONE[product.status]}>{t(`products.statuses.${product.status}`)}</Badge>
                  </td>
                  <td className={tdClass}>{formatDateTime(product.updatedAt)}</td>
                  <td className={tdClass}>
                    <div className="flex gap-1">
                      <Button
                        variant="tertiary"
                        size="sm"
                        onClick={() => {
                          openForm(product);
                        }}
                      >
                        {t("products.edit")}
                      </Button>
                      {product.status === "PUBLISHED" ? (
                        <Button
                          variant="tertiary"
                          size="sm"
                          disabled={setStatus.isPending || bulkRunning}
                          onClick={() => {
                            quickStatus(product, "ARCHIVED");
                          }}
                        >
                          {t("products.unpublish")}
                        </Button>
                      ) : (
                        <Button
                          variant="tertiary"
                          size="sm"
                          disabled={setStatus.isPending || bulkRunning}
                          onClick={() => {
                            quickStatus(product, "PUBLISHED");
                          }}
                        >
                          {t("products.publish")}
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      <Sheet
        open={editing !== null}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
      >
        <SheetContent className="mx-auto max-w-2xl">
          <SheetTitle>
            {isEditingExisting ? t("products.form.editTitle", { sku: form.sku }) : t("products.form.createTitle")}
          </SheetTitle>
          <SheetDescription>{t("products.form.description")}</SheetDescription>
          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <Input
              label={t("products.form.sku")}
              value={form.sku}
              disabled={isEditingExisting}
              helperText={t("products.form.skuHelp")}
              error={duplicateSku ? t("products.form.duplicateSku") : err("sku")}
              onChange={(event) => {
                set("sku", event.target.value.toUpperCase());
              }}
            />
            <Input
              label={t("products.form.name")}
              value={form.name}
              maxLength={180}
              error={err("name")}
              onChange={(event) => {
                set("name", event.target.value);
              }}
            />
            <Input
              label={t("products.form.price")}
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
              label={t("products.form.compareAt")}
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
              label={t("products.form.stock")}
              type="number"
              min={0}
              step={1}
              value={form.stockQuantity}
              error={err("stockQuantity")}
              onChange={(event) => {
                set("stockQuantity", event.target.value);
              }}
            />
            <div className="flex flex-col gap-1.5">
              <label htmlFor="seller-product-status" className="text-caption font-semibold text-text-secondary">
                {t("products.form.status")}
              </label>
              <select
                id="seller-product-status"
                className={`${controlClass} h-12`}
                value={form.status}
                onChange={(event) => {
                  set("status", event.target.value as AdminProductStatus);
                }}
              >
                {(["DRAFT", "PUBLISHED", "ARCHIVED"] as const).map((status) => (
                  <option key={status} value={status}>
                    {t(`products.statuses.${status}`)}
                  </option>
                ))}
              </select>
            </div>
            <div className="md:col-span-2">
              <Input
                label={t("products.form.imageUrl")}
                type="url"
                value={form.imageUrl}
                error={err("imageUrl")}
                onChange={(event) => {
                  set("imageUrl", event.target.value);
                }}
              />
            </div>
            <div className="flex flex-col gap-1.5 md:col-span-2">
              <label htmlFor="seller-product-description" className="text-caption font-semibold text-text-secondary">
                {t("products.form.descriptionLabel")}
              </label>
              <textarea
                id="seller-product-description"
                className={`${controlClass} min-h-28 py-2`}
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

          {conflict ? (
            <div
              role="alert"
              className="mt-4 flex flex-col gap-2 rounded-xl bg-danger-bg px-3 py-2 text-caption text-danger-text"
            >
              <p>{t("products.form.conflict")}</p>
              <div>
                <Button variant="secondary" size="sm" onClick={reloadLatest}>
                  {t("products.form.reload")}
                </Button>
              </div>
            </div>
          ) : mutationError && !duplicateSku ? (
            <p role="alert" className="mt-4 rounded-xl bg-danger-bg px-3 py-2 text-caption text-danger-text">
              {mutationError.message}
            </p>
          ) : null}

          <div className="mt-4 flex justify-end gap-2">
            <Button
              variant="tertiary"
              onClick={() => {
                setEditing(null);
              }}
            >
              {t("products.form.cancel")}
            </Button>
            <Button variant="primary" loading={mutation.isPending} disabled={conflict} onClick={submit}>
              {t("products.form.save")}
            </Button>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
