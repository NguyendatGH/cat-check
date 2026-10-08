import { useDeferredValue, useId, useMemo, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { MapPin, PawPrint, ShoppingBag } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { featureFlags } from "@/shared/config/featureFlags";
import { useCatList } from "@/features/cat";
import { listPlaces } from "@/features/place";
import { listShopProducts } from "@/features/shop";
import iconSearch from "@/shared/assets/icons/web-dashboard/header-search.svg";

/** Số ký tự tối thiểu trước khi lọc — 1 ký tự khớp gần như mọi thứ, chỉ gây nhiễu. */
const MIN_QUERY = 2;
const MAX_PER_GROUP = 4;
const SEARCH_STALE_MS = 5 * 60 * 1000;

type GroupKey = "cats" | "places" | "products";

interface SearchHit {
  id: string;
  group: GroupKey;
  to: string;
  title: string;
  subtitle: string | null;
}

const GROUP_ICON: Record<GroupKey, LucideIcon> = { cats: PawPrint, places: MapPin, products: ShoppingBag };
const GROUP_LABEL_KEY: Record<GroupKey, string> = {
  cats: "webShell.searchGroupCats",
  places: "webShell.searchGroupPlaces",
  products: "webShell.searchGroupProducts",
};

/**
 * So khớp không phân biệt hoa/thường và dấu tiếng Việt ("thao dien" khớp "Thảo Điền").
 * `đ/Đ` không tách được bằng NFD nên đổi tay.
 */
function normalize(value: string): string {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[đĐ]/g, "d")
    .toLowerCase()
    .trim();
}

function matches(needle: string, ...haystack: (string | null | undefined)[]): boolean {
  return haystack.some((h) => typeof h === "string" && normalize(h).includes(needle));
}

/**
 * Ô tìm kiếm ở header desktop (Figma 16:7475). Thiết kế ghi "Tìm kiếm kết quả quét, bệnh lý,
 * phòng khám…" nhưng ô đó trước đây KHÔNG làm gì và chữ "bệnh lý" là tuyên bố y tế. Nay nó
 * tìm thật trên ba nguồn có API:
 * - hồ sơ mèo — `GET /cats` (cùng query key với sidebar, không gọi thêm);
 * - phòng khám & cơ sở — `GET /places` (công khai);
 * - sản phẩm — `GET /shop/products` (công khai).
 * Lần quét không có endpoint tìm theo chữ nên KHÔNG được hứa trong placeholder.
 *
 * ponytail: lọc phía client trên tối đa 50 bản ghi mỗi danh sách (giới hạn mặc định của hai
 * endpoint) và bỏ qua tham số `query` của server — vì server so khớp có dấu ("thao" không ra
 * "Thảo Điền"). Trần: >50 cơ sở/sản phẩm thì phần dư không tìm thấy. Nâng cấp: server so khớp
 * bỏ dấu (`unaccent`) rồi chuyển sang `?query=` có debounce.
 */
export function HeaderSearch({ signedIn }: { signedIn: boolean }) {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  const listId = useId();
  const wrapperRef = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const deferred = useDeferredValue(query);
  const needle = normalize(deferred);
  const active = open && needle.length >= MIN_QUERY;

  const { data: catsPage } = useCatList("ACTIVE", signedIn);
  const places = useQuery({
    queryKey: ["shell-search", "places"],
    queryFn: () => listPlaces(),
    enabled: active && featureFlags.map,
    staleTime: SEARCH_STALE_MS,
  });
  const products = useQuery({
    queryKey: ["shell-search", "products"],
    queryFn: listShopProducts,
    enabled: active && featureFlags.shop,
    staleTime: SEARCH_STALE_MS,
  });

  const hits = useMemo<SearchHit[]>(() => {
    if (needle.length < MIN_QUERY) return [];
    const cats = (catsPage?.items ?? [])
      .filter((c) => matches(needle, c.name, c.breedName, c.publicCode))
      .slice(0, MAX_PER_GROUP)
      .map<SearchHit>((c) => ({
        id: c.id,
        group: "cats",
        to: `/cats/${c.id}`,
        title: c.name,
        subtitle: c.breedName ?? c.publicCode,
      }));
    const placeHits = (places.data ?? [])
      .filter((p) => matches(needle, p.name, p.address, p.area))
      .slice(0, MAX_PER_GROUP)
      .map<SearchHit>((p) => ({
        id: p.id,
        group: "places",
        to: `/map/clinics/${p.id}`,
        title: p.name,
        subtitle: p.address,
      }));
    const productHits = (products.data ?? [])
      .filter((p) => matches(needle, p.name, p.description, p.sku))
      .slice(0, MAX_PER_GROUP)
      .map<SearchHit>((p) => ({
        id: p.id,
        group: "products",
        to: `/shop/products/${p.id}`,
        title: p.name,
        subtitle: p.description,
      }));
    return [...cats, ...placeHits, ...productHits];
  }, [needle, catsPage, places.data, products.data]);

  const loading = (featureFlags.map && places.isPending) || (featureFlags.shop && products.isPending);
  const failed = places.isError && products.isError;
  const showPanel = active;
  const current = hits.length > 0 ? Math.min(activeIndex, hits.length - 1) : -1;

  const close = () => {
    setOpen(false);
    setActiveIndex(0);
  };

  const go = (hit: SearchHit) => {
    close();
    setQuery("");
    void navigate(hit.to);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Escape") {
      close();
      return;
    }
    if (!showPanel || hits.length === 0) return;
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIndex((i) => (i + 1) % hits.length);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIndex((i) => (i - 1 + hits.length) % hits.length);
    } else if (e.key === "Enter" && current >= 0) {
      e.preventDefault();
      go(hits[current]);
    }
  };

  const groups = (["cats", "places", "products"] as const)
    .map((group) => ({ group, items: hits.filter((h) => h.group === group) }))
    .filter((g) => g.items.length > 0);

  return (
    <div
      ref={wrapperRef}
      className="relative flex h-10 min-w-0 max-w-[549px] flex-1 items-center"
      onBlur={(e) => {
        if (!wrapperRef.current?.contains(e.relatedTarget)) close();
      }}
    >
      <img src={iconSearch} alt="" className="pointer-events-none absolute left-3 size-[15px]" />
      <input
        type="search"
        role="combobox"
        aria-expanded={showPanel}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showPanel && current >= 0 ? `${listId}-${String(current)}` : undefined}
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
          setActiveIndex(0);
        }}
        onFocus={() => {
          setOpen(true);
        }}
        onKeyDown={onKeyDown}
        placeholder={t("webShell.searchPlaceholder")}
        aria-label={t("webShell.searchLabel")}
        className="h-10 w-full rounded-xl bg-background-alt pl-10 pr-4 text-caption text-text-primary placeholder:text-text-tertiary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
      />

      {showPanel ? (
        <div
          id={listId}
          role="listbox"
          aria-label={t("webShell.searchLabel")}
          className="absolute inset-x-0 top-12 z-[var(--z-dropdown)] max-h-[420px] overflow-y-auto rounded-xl border border-border bg-surface p-2 shadow-brand-lg"
        >
          {groups.length > 0 ? (
            groups.map(({ group, items }) => {
              const Icon = GROUP_ICON[group];
              return (
                <div key={group} role="group" aria-label={t(GROUP_LABEL_KEY[group])} className="py-1">
                  <p className="px-2 pb-1 text-overline tracking-[0.4px] text-text-tertiary">
                    {t(GROUP_LABEL_KEY[group])}
                  </p>
                  {items.map((hit) => {
                    const index = hits.indexOf(hit);
                    return (
                      <Link
                        key={hit.id}
                        id={`${listId}-${String(index)}`}
                        role="option"
                        aria-selected={index === current}
                        to={hit.to}
                        onClick={(e) => {
                          e.preventDefault();
                          go(hit);
                        }}
                        onMouseEnter={() => {
                          setActiveIndex(index);
                        }}
                        className={cn(
                          "flex min-h-11 items-center gap-3 rounded-lg px-2 py-1.5",
                          index === current ? "bg-background-alt" : "hover:bg-background-alt",
                        )}
                      >
                        <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-primary-dark">
                          <Icon size={16} aria-hidden="true" />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-caption font-semibold text-text-primary">
                            {hit.title}
                          </span>
                          {hit.subtitle ? (
                            <span className="block truncate text-small text-text-secondary">{hit.subtitle}</span>
                          ) : null}
                        </span>
                      </Link>
                    );
                  })}
                </div>
              );
            })
          ) : (
            <p className="px-2 py-3 text-caption text-text-secondary" role="status">
              {loading
                ? t("webShell.searchLoading")
                : failed
                  ? t("webShell.searchError")
                  : t("webShell.searchEmpty", { query: deferred.trim() })}
            </p>
          )}
        </div>
      ) : null}
    </div>
  );
}
