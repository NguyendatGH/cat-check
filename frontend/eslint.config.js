// @ts-check
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";
import jsxA11y from "eslint-plugin-jsx-a11y";
import i18next from "eslint-plugin-i18next";
import boundaries from "eslint-plugin-boundaries";

/**
 * eslint.config.js — flat config. Cấu trúc theo p9/p10 mục 4 (M0 CatCheck frontend):
 *  1) ignore toàn cục
 *  2) base rules (JS recommended + typescript-eslint strictTypeChecked)
 *  3) react-hooks + jsx-a11y + i18next
 *  4) boundaries — ranh giới kiến trúc 6 element type (app/page/feature/entity/shared/root)
 *  5) rule bổ sung: cấm recharts/firebase/openapi-fetch ngoài phạm vi, cấm import.meta.env
 *     ngoài shared/config/env.ts, cấm hex literal, cấm literal pH hard-code
 *  6) override cho từng ngoại lệ (features/trends, features/export, features/notification,
 *     src/sw.ts, shared/api, shared/config/env.ts)
 *
 * Ghi chú kỹ thuật: `boundaries/element-types` là rule ĐÃ DEPRECATED trong
 * eslint-plugin-boundaries@7 (thay bằng `boundaries/dependencies` hợp nhất), nhưng spec
 * (p9 mục 4) chỉ định đích danh rule này nên giữ nguyên — vẫn hoạt động đầy đủ qua lớp
 * tương thích ngược của plugin, chỉ in 1 dòng cảnh báo migrate (đã tắt qua
 * `boundaries/legacy-warnings: false` bên dưới để log CI sạch).
 */

const RESTRICTED_IMPORTS_BASE = [
  {
    id: "recharts",
    entry: {
      paths: [
        {
          name: "recharts",
          message: "recharts chỉ được import trong features/trends và features/export (lazy-load, xem p9 mục 1).",
        },
      ],
    },
  },
  {
    id: "firebase",
    entry: {
      patterns: [
        {
          group: ["firebase", "firebase/*"],
          message: "firebase/* chỉ được import trong features/notification và src/sw.ts.",
        },
      ],
    },
  },
  {
    id: "openapi-fetch",
    entry: {
      paths: [
        {
          name: "openapi-fetch",
          message:
            "openapi-fetch chỉ được import trực tiếp trong shared/api (dùng shared/api/client thay vì import thẳng).",
        },
      ],
    },
  },
];

/** Build cấu hình no-restricted-imports, loại trừ 1 vài rule theo id (dùng cho override theo path). */
function restrictedImports(excludeIds = []) {
  const active = RESTRICTED_IMPORTS_BASE.filter((r) => !excludeIds.includes(r.id));
  return {
    paths: active.flatMap((r) => r.entry.paths ?? []),
    patterns: active.flatMap((r) => r.entry.patterns ?? []),
  };
}

export default tseslint.config(
  // 1) Ignore toàn cục
  {
    ignores: [
      "dist/**",
      "dev-dist/**",
      "coverage/**",
      "node_modules/**",
      "public/**",
      "src/shared/api/schema.d.ts",
      "playwright-report/**",
      "test-results/**",
      ".shot-*.mjs",
    ],
  },

  // 2) Base JS + TS (strictTypeChecked)
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/no-floating-promises": "error",
      "@typescript-eslint/no-misused-promises": "error",
    },
  },

  // sw.ts chạy trong ServiceWorkerGlobalScope, không thuộc project TS chính (xem
  // tsconfig.app.json exclude) — tắt riêng type-checked rules cần "project" cho file này,
  // giữ rule JS thường.
  {
    files: ["src/sw.ts"],
    ...tseslint.configs.disableTypeChecked,
  },

  // 3) react-hooks + jsx-a11y + i18next — chỉ áp cho file ứng dụng (tsx/ts trong src/)
  //
  // Ghi chú: eslint-plugin-react-hooks@7 (pin theo p9) đổi "recommended" thành bộ rule mới
  // hướng React Compiler (static-components, use-memo, immutability, refs...) rộng hơn
  // nhiều so với yêu cầu spec (chỉ nêu đích danh "exhaustive-deps: error"). Chỉ bật 2 rule
  // cốt lõi cổ điển (rules-of-hooks + exhaustive-deps) để tránh lỗi ngoài phạm vi M0.
  {
    files: ["src/**/*.{ts,tsx}"],
    plugins: {
      "react-hooks": reactHooks.configs.flat.recommended.plugins["react-hooks"],
    },
    rules: {
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "error",
    },
  },
  {
    files: ["src/**/*.{ts,tsx}"],
    ...jsxA11y.flatConfigs.recommended,
  },
  {
    files: ["src/**/*.{ts,tsx}"],
    plugins: { i18next },
    rules: {
      "i18next/no-literal-string": [
        "error",
        {
          mode: "jsx-text-only",
          "should-validate-template": true,
        },
      ],
    },
  },

  // 4) Boundaries — ranh giới kiến trúc
  {
    files: ["src/**/*.{ts,tsx}"],
    plugins: { boundaries },
    settings: {
      "boundaries/elements": [
        { type: "app", pattern: "src/app/**", partialMatch: false },
        { type: "page", pattern: "src/pages/*/**", capture: ["page"], partialMatch: false },
        { type: "feature", pattern: "src/features/*/**", capture: ["feature"], partialMatch: false },
        { type: "entity", pattern: "src/entities/*/**", capture: ["entity"], partialMatch: false },
        { type: "shared", pattern: "src/shared/**", partialMatch: false },
        { type: "root", pattern: "src/*.{ts,tsx}", partialMatch: false },
      ],
      // eslint-plugin-boundaries dùng eslint-module-utils/resolve (hạ tầng resolver của
      // eslint-plugin-import) để suy ra file đích của 1 import. Resolver "node" mặc định
      // không hiểu path alias "@/*" (tsconfig paths) và không thử đuôi .ts/.tsx — mọi import
      // "@/..." (dùng ở HẦU HẾT codebase) sẽ resolve ra "unknown" và bị bỏ qua hoàn toàn,
      // khiến rule boundaries im lặng không check gì (đã kiểm chứng thực tế). Thêm
      // eslint-import-resolver-typescript (đọc thẳng tsconfig paths + đuôi .ts/.tsx) — 1
      // devDependency bổ sung hợp lý ngoài danh sách pin gốc, cần thiết để rule boundaries
      // hoạt động thật trên codebase dùng alias "@/*".
      "import/resolver": {
        typescript: { project: "./tsconfig.app.json" },
      },
    },
    rules: {
      // Dùng rule HỢP NHẤT hiện hành `boundaries/dependencies` thay vì
      // `boundaries/element-types`/`boundaries/entry-point` (tên spec p9 mục 4 nêu — 2 rule
      // đó vẫn tồn tại trong eslint-plugin-boundaries@7.2.0 nhưng đã DEPRECATED và, đã kiểm
      // chứng thực tế (import trái phép giữa 2 feature qua đúng index.ts KHÔNG bị báo lỗi),
      // lớp tương thích ngược của chúng không còn nhận diện đúng selector dạng
      // "capture theo chính nó" (${from.feature}) nên policy feature/entity bị bỏ qua lặng
      // lẽ. `boundaries/dependencies` triển khai đúng ma trận mô tả trong spec, đã test thủ
      // công (feature X import feature Y qua index.ts -> lỗi; qua entry-point sai -> lỗi).
      "boundaries/dependencies": [
        "error",
        {
          default: "disallow",
          policies: [
            // Entry-point (p9 mục 4): feature/entity CHỈ được vào TỪ BÊN NGOÀI qua index.ts
            // — biểu diễn bằng `fileInternalPath: "**/index.ts"` ngay trong MỖI selector
            // "to" nhắm tới feature/entity bên dưới (đã kiểm chứng thủ công: import sâu
            // kiểu features/scan/model/x bị chặn, import qua features/scan (index.ts) thì
            // qua). Import NỘI BỘ trong CHÍNH element đó (from và to cùng capture) là quan
            // hệ "internal" nên plugin tự bỏ qua rule này (checkInternals mặc định false),
            // do đó feature/entity vẫn tự do import sâu file của CHÍNH MÌNH.
            {
              from: { element: { type: "app" } },
              allow: [
                { to: { element: { types: { anyOf: ["page", "shared"] } } } },
                { to: { element: { types: { anyOf: ["feature", "entity"] }, fileInternalPath: "**/index.ts" } } },
              ],
            },
            {
              from: { element: { type: "page" } },
              allow: [
                { to: { element: { type: "shared" } } },
                { to: { element: { types: { anyOf: ["feature", "entity"] }, fileInternalPath: "**/index.ts" } } },
              ],
            },
            {
              from: { element: { type: "feature" } },
              allow: [
                // feature -> entity: cho phép MỌI entity (đa số feature cần nhiều entity,
                // ví dụ features/trends cần entities/scan-result + entities/ph-bands).
                { to: { element: { type: "entity", fileInternalPath: "**/index.ts" } } },
                { to: { element: { type: "shared" } } },
              ],
            },
            {
              from: { element: { type: "entity" } },
              allow: [
                { to: { element: { type: "shared" } } },
                // NGOẠI LỆ TƯỜNG MINH DUY NHẤT (p9 mục 4): entities/user được entity khác
                // import (bình thường 1 entity KHÔNG được import entity khác) — session/
                // entitlement dùng khắp app.
                { to: { element: { type: "entity", captured: { entity: "user" }, fileInternalPath: "**/index.ts" } } },
              ],
            },
            {
              from: { element: { type: "shared" } },
              allow: { to: { element: { type: "shared" } } },
            },
            {
              from: { element: { type: "root" } },
              allow: { to: { element: { types: { anyOf: ["app", "shared"] } } } },
            },
          ],
        },
      ],
    },
  },

  // 5) Rule bổ sung — import bị giới hạn phạm vi
  {
    files: ["src/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": ["error", restrictedImports()],
    },
  },
  // Ngoại lệ: recharts
  {
    files: ["src/features/trends/**/*.{ts,tsx}", "src/features/export/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": ["error", restrictedImports(["recharts"])],
    },
  },
  // Ngoại lệ: firebase/*
  {
    files: ["src/features/notification/**/*.{ts,tsx}", "src/sw.ts"],
    rules: {
      "no-restricted-imports": ["error", restrictedImports(["firebase"])],
    },
  },
  // Ngoại lệ: openapi-fetch
  {
    files: ["src/shared/api/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": ["error", restrictedImports(["openapi-fetch"])],
    },
  },

  // Cấm import.meta.env ngoài shared/config/env.ts + cấm hex literal (.tsx) + cấm literal pH
  {
    files: ["src/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-syntax": [
        "error",
        {
          selector: "MemberExpression[object.type='MetaProperty'][property.name='env']",
          message: "import.meta.env chỉ được đọc trong shared/config/env.ts (fail-fast bằng zod ở đó).",
        },
        {
          selector: "Identifier[name=/^PH_(REFERENCE|RANGE|BAND)/]",
          message: "Không hard-code identifier ngưỡng pH — ngưỡng luôn đến từ API (entities/ph-bands).",
        },
        {
          selector:
            "VariableDeclarator[id.name=/[Pp][Hh]/] > Literal[value=6.3], VariableDeclarator[id.name=/[Pp][Hh]/] > Literal[value=6.6], VariableDeclarator[id.name=/[Pp][Hh]/] > Literal[value=5.5], VariableDeclarator[id.name=/[Pp][Hh]/] > Literal[value=8]",
          message: "Không hard-code giá trị ngưỡng pH vào biến — ngưỡng luôn đến từ API (entities/ph-bands).",
        },
      ],
    },
  },
  {
    files: ["src/shared/config/env.ts"],
    rules: { "no-restricted-syntax": "off" },
  },
  {
    files: ["src/**/*.tsx"],
    rules: {
      "no-restricted-syntax": [
        "error",
        {
          selector: "Literal[value=/#[0-9a-fA-F]{3,8}/]",
          message: "Không hard-code hex màu trong .tsx — dùng token Tailwind (@theme) hoặc class utility.",
        },
      ],
    },
  },
  // Ngoại lệ: brand mark bên thứ ba (Google "G" 4 màu, MoMo magenta) không thuộc hệ token màu —
  // đặt SAU block hex ở trên để thắng (flat config gộp theo thứ tự, file trùng cả hai glob).
  {
    files: ["src/shared/assets/icons/GoogleGlyph.tsx", "src/shared/assets/icons/MoMoGlyph.tsx"],
    rules: { "no-restricted-syntax": "off" },
  },

  // 6) Ngoại lệ entry-point: index.ts của mỗi feature/entity được PHÉP import sâu chính nó
  // (rule entry-point chỉ chặn import TỪ BÊN NGOÀI feature/entity, file index.ts nằm bên
  // trong nên không bị chặn — không cần override thêm).

  // Config files ở root (không thuộc src/) — tắt rule type-checked/boundaries, chỉ cần TS cơ bản.
  {
    files: ["*.config.{js,ts}", "*.config.*.{js,ts}"],
    ...tseslint.configs.disableTypeChecked,
  },
);
