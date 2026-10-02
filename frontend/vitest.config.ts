import { defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config";

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: "jsdom",
      globals: true,
      css: true,
      setupFiles: [],
      exclude: ["node_modules", "dist", "e2e"],
    },
  }),
);
