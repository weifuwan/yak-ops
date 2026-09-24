import { fileURLToPath, URL } from "node:url";

import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [tailwindcss()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
      "@yak-ops/yak-ui": fileURLToPath(
        new URL("./packages/yak-ui/src/index.ts", import.meta.url),
      ),
      "@yak-ops/datasource": fileURLToPath(
        new URL("./packages/datasource/src/index.tsx", import.meta.url),
      ),
    },
  },
  server: {
    proxy: {
      "/api": "http://127.0.0.1:8080",
      "/yak-security": "http://127.0.0.1:8080",
    },
  },
});
