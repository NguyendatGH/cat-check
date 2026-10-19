import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Hai kiểm tra chặn "lộ khoá thô" trên UI: (1) vi và en phải cùng một bộ khoá, (2) mọi khoá
 * `t("...")` viết thẳng trong code phải có trong locales/vi (en rơi về vi qua fallbackLng).
 */

const I18N_DIR = import.meta.dirname;
const SRC_DIR = join(I18N_DIR, "..", "..");

/** Trả về `đường dẫn khoá → true nếu là lá (chuỗi), false nếu là object/mảng`. */
function flatten(value: unknown, path: string, out: Map<string, boolean>): void {
  if (path) out.set(path, typeof value === "string");
  if (Array.isArray(value)) {
    value.forEach((v, i) => {
      flatten(v, `${path}[${String(i)}]`, out);
    });
  } else if (value !== null && typeof value === "object") {
    for (const [k, v] of Object.entries(value)) {
      flatten(v, path ? `${path}.${k}` : k, out);
    }
  }
}

function load(lng: "vi" | "en"): Record<string, Map<string, boolean>> {
  const dir = join(I18N_DIR, "locales", lng);
  const byNs: Record<string, Map<string, boolean>> = {};
  for (const file of readdirSync(dir).filter((f) => f.endsWith(".json"))) {
    const keys = new Map<string, boolean>();
    flatten(JSON.parse(readFileSync(join(dir, file), "utf8")), "", keys);
    byNs[file.replace(".json", "")] = keys;
  }
  return byNs;
}

const vi = load("vi");
const en = load("en");

/** i18next đặt hậu tố số nhiều (`_one`/`_other`) tuỳ ngôn ngữ: vi không có, en có. */
const stripPlural = (key: string) => key.replace(/_(zero|one|two|few|many|other)$/, "");

/** Khoá chỉ có ở một bên có chủ đích. */
const EN_ONLY = [/^common:healthFlags\.message(\.|$)/];

describe("vi và en cùng một bộ khoá", () => {
  it("cùng danh sách namespace", () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(vi).sort());
  });

  it.each(Object.keys(vi))("%s: không lệch khoá giữa vi và en", (ns) => {
    const norm = (keys: Map<string, boolean>) =>
      new Set([...keys.keys()].map(stripPlural).filter((k) => !EN_ONLY.some((re) => re.test(`${ns}:${k}`))));
    const viKeys = norm(vi[ns]);
    const enKeys = norm(en[ns]);

    expect({
      thieuOEn: [...viKeys].filter((k) => !enKeys.has(k)),
      thieuOVi: [...enKeys].filter((k) => !viKeys.has(k)),
    }).toEqual({ thieuOEn: [], thieuOVi: [] });
  });
});

describe("khoá t() viết thẳng trong code có trong locales/vi", () => {
  // Bỏ test, mock và d.ts. Khoá động (template literal, biến) không khớp regex nên tự bị bỏ qua.
  const files = readdirSync(SRC_DIR, { recursive: true, encoding: "utf8" })
    .filter((f) => /\.tsx?$/.test(f) && !/\.(test|d)\.tsx?$|(^|[\\/])(mocks?|__mocks__)([\\/.]|$)/.test(f))
    .map((f) => f.replaceAll("\\", "/"));
  const viByNs: Partial<typeof vi> = vi; // ns gõ sai thì không có trong map

  // Dương tính giả đã biết: `t` không kiểu truyền làm tham số trong AdminLayout (ns thật là admin).
  const IGNORE = [/^common:nav\.label$/];

  it("không có khoá nào thiếu", () => {
    const missing: string[] = [];
    let checked = 0;
    for (const file of files) {
      const src = readFileSync(join(SRC_DIR, file), "utf8");
      // Chỉ hook gán cho `t` (bỏ `{ t: tAdmin }`) và kiểu `TFunction<"ns">` của tham số.
      const hooks = [
        ...src.matchAll(/(?:\{\s*t\s*[,}][^=\n]*=\s*useTranslation\(|TFunction<)(?:\s*\[?\s*["'](\w+)["'])?/g),
      ];
      for (const m of src.matchAll(/\bt\(\s*["']([\w.:-]+)["']([^)]*)/g)) {
        // Namespace theo thứ tự: tiền tố `ns:`, tuỳ chọn `{ ns: "..." }`, rồi hook gần nhất phía trên.
        const sep = m[1].indexOf(":");
        const ns =
          sep >= 0
            ? m[1].slice(0, sep)
            : (/\bns:\s*["'](\w+)["']/.exec(m[2])?.[1] ?? hooks.findLast((h) => h.index < m.index)?.[1] ?? "common");
        const key = m[1].slice(sep + 1);
        const full = `${ns}:${key}`;
        if (IGNORE.some((re) => re.test(full))) continue;
        checked++;
        // has() đúng cả khoá lá lẫn khoá cha (returnObjects); `key_other` là dạng số nhiều.
        if (!viByNs[ns]?.has(key) && !viByNs[ns]?.has(`${key}_other`)) missing.push(`${file}: ${full}`);
      }
    }
    expect(checked, "regex quét không bắt được lệnh t() nào").toBeGreaterThan(1000);
    expect(missing).toEqual([]);
  });
});
