import { fileURLToPath, URL } from "node:url";

import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

const webRoot = fileURLToPath(new URL(".", import.meta.url));
const workspaceRoot = fileURLToPath(new URL("../..", import.meta.url));

export default defineConfig({
  root: webRoot,
  publicDir: fileURLToPath(new URL("./public", import.meta.url)),
  plugins: [tailwindcss()],
  resolve: {
    alias: {
      "@": webRoot,
      "@yak-ops/yak-ui": fileURLToPath(
        new URL("../../packages/yak-ui/src/index.ts", import.meta.url),
      ),
      "@yak-ops/datasource": fileURLToPath(
        new URL("../../packages/datasource/src/index.tsx", import.meta.url),
      ),
    },
  },
  server: {
    fs: {
      allow: [workspaceRoot],
    },
    proxy: {
      "/api": "http://127.0.0.1:8080",
      "/yak-security": "http://127.0.0.1:8080",
    },
  },
  build: {
    outDir: fileURLToPath(new URL("../../dist", import.meta.url)),
    emptyOutDir: true,
  },
});
