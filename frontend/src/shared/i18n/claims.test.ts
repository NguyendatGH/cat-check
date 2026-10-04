import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * REQ-CLAIM-04 (p15 §15.9): danh sách cụm từ cấm phải được quét trên **cả file i18n**, không
 * chỉ nội dung CMS, và test **chặn merge**.
 *
 * Vì sao cần: p14 §14.3.5(d) đã seed danh sách chặn vào `app_setting['content.blocked_terms']`
 * và p17 AD23 kiểm nó — nhưng chỉ cho nội dung publish qua admin CMS. Chuỗi i18n tĩnh không đi
 * qua cổng đó, nên claim chưa kiểm chứng lọt vào sản phẩm mà không test nào đỏ.
 *
 * ponytail: đây là test theo ĐƯỜNG CƠ SỞ, không phải test tuyệt đối. Hai trần của nó:
 * (a) 21 claim đang tồn tại trong i18n được ghi ở `claims-baseline.json` và test KHÔNG đỏ vì
 * chúng; (b) test chỉ quét `locales/vi/**`, KHÔNG quét `src/pages/*\/mockData.ts` — bảng rà
 * soát đếm 75 claim vì nó tính cả mock data. Mở rộng sang mock data là việc tiếp theo — xoá sạch hôm nay là
 * bất khả thi vì REQ-CLAIM-06 nói phần lớn phải chờ owner trả lời bốn câu ở
 * `context/docs/claim-review.md`. Cái test này chặn claim MỚI. Đường nâng cấp: mỗi lần DPO
 * quyết một dòng thì xoá chuỗi khỏi code VÀ khỏi baseline; khi baseline rỗng thì đổi
 * `expect(unknown)` thành `expect(found)` để thành kiểm tuyệt đối, rồi xoá file baseline.
 */

/** Đúng danh sách REQ-CLAIM-04, cộng các biến thể đã thấy trong code. */
const BANNED: { label: string; pattern: RegExp }[] = [
  { label: "HIPAA", pattern: /\bHIPAA\b/i },
  { label: "VET-SECURE", pattern: /VET-SECURE/i },
  { label: "ISO/Vet-17025", pattern: /ISO\/Vet-17025/i },
  { label: "ISO/IEC 27001", pattern: /ISO\/IEC\s*27001/i },
  { label: "ISFM", pattern: /\bISFM\b/ },
  { label: "WVA", pattern: /\bWVA\b/ },
  { label: "Hội đồng Chăm sóc Tiết niệu Mèo", pattern: /Hội đồng Chăm sóc Tiết niệu Mèo/i },
  { label: "Bác sĩ 24/7", pattern: /Bác sĩ.{0,12}24\/7/i },
  { label: "Feline Health Privacy", pattern: /Feline Health Privacy/i },
  // REQ-CLAIM-04: "mọi chuỗi khớp \d{2,3}[.,]\d% trong copy marketing" — con số độ chính xác
  // kiểu "99,4%" / "98.4%" cần báo cáo đo thực nghiệm (p1 §1.8 còn để ngỏ chỉ tiêu này).
  { label: "số % có thập phân", pattern: /\d{2,3}[.,]\d\s*%/ },
];

const LOCALE_DIR = join(import.meta.dirname, "locales", "vi");

// Đọc bằng readFileSync thay vì `import ... from "*.json"`: `resolveJsonModule` chưa bật ở
// tsconfig.app.json, và bật nó lên cho cả dự án chỉ để một test đọc một file là đổi cấu hình
// rộng hơn mức cần. Cách này cũng giống hệt cách test đọc các file locale ngay bên dưới.
const baseline = JSON.parse(readFileSync(join(import.meta.dirname, "claims-baseline.json"), "utf8")) as string[];

interface Hit {
  file: string;
  key: string;
  term: string;
  text: string;
}

function flatten(value: unknown, path: string, out: [string, string][]): void {
  if (typeof value === "string") {
    out.push([path, value]);
  } else if (Array.isArray(value)) {
    value.forEach((v, i) => {
      flatten(v, `${path}[${String(i)}]`, out);
    });
  } else if (value !== null && typeof value === "object") {
    for (const [k, v] of Object.entries(value)) {
      flatten(v, path ? `${path}.${k}` : k, out);
    }
  }
}

function scan(): Hit[] {
  const hits: Hit[] = [];
  for (const file of readdirSync(LOCALE_DIR).filter((f) => f.endsWith(".json"))) {
    const pairs: [string, string][] = [];
    flatten(JSON.parse(readFileSync(join(LOCALE_DIR, file), "utf8")), "", pairs);
    for (const [key, text] of pairs) {
      for (const { label, pattern } of BANNED) {
        if (pattern.test(text)) {
          hits.push({ file: file.replace(".json", ""), key, term: label, text });
          break;
        }
      }
    }
  }
  return hits;
}

describe("REQ-CLAIM-04 — cụm từ cấm trong i18n", () => {
  it("không có claim chưa kiểm chứng MỚI nào", () => {
    const known = new Set(baseline);
    const unknown = scan().filter((h) => !known.has(`${h.file}:${h.key}`));

    const detail = unknown.map((h) => `  ${h.file}:${h.key}\n    [${h.term}] ${h.text.slice(0, 120)}`).join("\n");

    expect(
      unknown,
      `\nClaim chưa kiểm chứng MỚI (p15 REQ-CLAIM-02/04):\n${detail}\n\n` +
        "Mỗi dòng cần một trong hai: (a) bằng chứng đúng mức REQ-CLAIM-02 — số hiệu chứng chỉ, " +
        "văn bản thoả thuận, báo cáo đo, hoặc trích dẫn nguồn — rồi thêm vào " +
        "context/docs/claim-review.md với trạng thái GIỮ và chữ ký DPO; hoặc (b) viết lại bỏ claim.\n" +
        "KHÔNG thêm vào claims-baseline.json để test xanh: baseline chỉ ghi nợ cũ, không nhận nợ mới.\n",
    ).toEqual([]);
  });

  it("baseline không chứa dòng đã biến mất khỏi code", () => {
    // Giữ baseline co lại theo thực tế: khi một claim được xoá khỏi i18n thì dòng tương ứng
    // phải rời baseline, nếu không baseline sẽ che mất claim mới trùng khoá sau này.
    const current = new Set(scan().map((h) => `${h.file}:${h.key}`));
    const stale = baseline.filter((k) => !current.has(k));

    expect(
      stale,
      `\nBaseline còn ${String(stale.length)} dòng không còn trong i18n — xoá chúng khỏi ` +
        `src/shared/i18n/claims-baseline.json:\n${stale.join("\n")}\n`,
    ).toEqual([]);
  });
});
