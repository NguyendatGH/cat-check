import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";
import { fileURLToPath, URL } from "node:url";
import { cpSync, existsSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";

const rootDir = dirname(fileURLToPath(import.meta.url));

/**
 * i18next-http-backend nạp JSON từ /locales/{{lng}}/{{ns}}.json lúc runtime (fetch), nhưng
 * nguồn sự thật của nội dung dịch là src/shared/i18n/locales/** (cây thư mục bắt buộc theo
 * spec p9). Đồng bộ 1 chiều src -> public/locales mỗi lần chạy vite (dev/build/preview) để
 * không phải duy trì 2 bản thủ công. public/locales/ được sinh ra — KHÔNG sửa tay ở đó.
 */
function syncLocalesToPublic(): void {
  const src = join(rootDir, "src/shared/i18n/locales");
  const dest = join(rootDir, "public/locales");
  if (!existsSync(src)) return;
  mkdirSync(dest, { recursive: true });
  cpSync(src, dest, { recursive: true });
}

syncLocalesToPublic();

/**
 * MapLibre 6 ships its worker as an ES module that imports a sibling shared module. Vite's
 * `?url` asset import copies only the worker, so the browser would receive the SPA HTML for
 * that sibling and report `Unexpected token '<'`. Keep both files together under public so the
 * worker remains self-hosted in dev, preview and production builds.
 */
function syncMapLibreWorkerToPublic(): void {
  const sourceDir = join(rootDir, "node_modules/maplibre-gl/dist");
  const targetDir = join(rootDir, "public/maplibre");
  if (!existsSync(sourceDir)) return;
  mkdirSync(targetDir, { recursive: true });
  cpSync(join(sourceDir, "maplibre-gl-worker.mjs"), join(targetDir, "maplibre-gl-worker.mjs"));
  cpSync(join(sourceDir, "maplibre-gl-shared.mjs"), join(targetDir, "maplibre-gl-shared.mjs"));
}

syncMapLibreWorkerToPublic();

/**
 * Đồng bộ LẠI mỗi khi file nguồn đổi, không chỉ lúc khởi động.
 *
 * Bug thật đã sửa: `syncLocalesToPublic()` ở trên chạy đúng một lần lúc nạp config. Thêm một
 * khoá i18n trong lúc dev server đang chạy ⇒ bản sao ở `public/locales` cũ ⇒ giao diện hiện
 * KHOÁ THÔ (`webShell.switcherTitle`) cho tới khi ai đó nhớ restart. Đã cắn nhiều lần, và mỗi
 * lần đều phải chép tay bù. Vite đã có sẵn watcher; chỉ cần bảo nó để mắt tới thư mục nguồn.
 */
function localeSyncPlugin() {
  const src = join(rootDir, "src/shared/i18n/locales");
  return {
    name: "catcheck-locale-sync",
    configureServer(server: {
      watcher: { add: (p: string) => void; on: (e: string, cb: (f: string) => void) => void };
    }) {
      server.watcher.add(src);
      const onChange = (file: string) => {
        if (file.startsWith(src)) syncLocalesToPublic();
      };
      server.watcher.on("add", onChange);
      server.watcher.on("change", onChange);
      server.watcher.on("unlink", onChange);
    },
  };
}

// M0: injectManifest strategy — src/sw.ts owns precache + (future) FCM background
// handler. See src/sw.ts for the Workbox setup.
const BACKEND_PROXY = { target: "http://localhost:8080", changeOrigin: true };

const API_PROXY = {
 "/api": BACKEND_PROXY,
  "/oauth2": BACKEND_PROXY,
  "/login/oauth2": BACKEND_PROXY,
};

export default defineConfig({
  plugins: [
    localeSyncPlugin(),
    react(),
    tailwindcss(),
    VitePWA({
      strategies: "injectManifest",
      srcDir: "src",
      filename: "sw.ts",
      registerType: "prompt",
      injectRegister: false,
      manifest: {
        name: "CatCheck",
        short_name: "CatCheck",
        description: "Theo dõi sức khoẻ mèo qua cát vệ sinh đổi màu theo pH",
        theme_color: "#2F4FB2",
        background_color: "#FAF8FF",
        display: "standalone",
        start_url: "/",
        scope: "/",
        icons: [
          {
            src: "/icons/pwa-192x192.png",
            sizes: "192x192",
            type: "image/png",
          },
          {
            src: "/icons/pwa-512x512.png",
            sizes: "512x512",
            type: "image/png",
          },
          {
            src: "/icons/maskable-512x512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      injectManifest: {
        // Bug thật đã sửa: glob trước đây thiếu `json`, nên `/locales/{lng}/{ns}.json` KHÔNG
        // vào precache. i18next-http-backend nạp các file đó bằng `fetch` lúc chạy, nên khi
        // mất mạng chính trang `/offline` lại hiện ra khoá i18n thô ("errors.offlinePage.title")
        // thay vì câu tiếng Việt — đúng màn hình mà người dùng chỉ thấy khi đã mất mạng.
        // Chỉ thêm `locales/**/*.json` chứ không mở `**/*.json` cho cả dist: 18 file, 180KB.
        globPatterns: ["**/*.{js,css,html,svg,woff2}", "locales/**/*.json"],
      },
      devOptions: {
        enabled: false,
        type: "module",
      },
    }),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  // Dev (`vite`) va preview (`vite preview`) dung CHUNG mot proxy.
  //
  // Bug that da sua: proxy truoc day chi khai o `server`, nen `vite preview` (cong 4173 —
  // dung cho `npm run e2e`, xem playwright.config.ts) KHONG chuyen tiep `/api` sang backend.
  // Hau qua: moi e2e buoc phai mock API, khong the kiem duoc luong that.
  server: { proxy: API_PROXY },
  preview: { proxy: API_PROXY },
});
