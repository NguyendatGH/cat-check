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

// M0: injectManifest strategy — src/sw.ts owns precache + (future) FCM background
// handler. See src/sw.ts for the Workbox setup.
export default defineConfig({
  plugins: [
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
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
