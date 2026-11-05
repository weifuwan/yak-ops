import { fileURLToPath, URL } from "node:url";

import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

const webRoot = fileURLToPath(new URL(".", import.meta.url));
const backendUrl = process.env.YAK_BACKEND_URL || "http://127.0.0.1:8080";
const yakOpsBackendUrl = process.env.YAK_OPS_BACKEND_URL || "http://127.0.0.1:12345";
const workspaceRoot = fileURLToPath(new URL("../..", import.meta.url));

export default defineConfig({
  root: webRoot,
  publicDir: fileURLToPath(new URL("./public", import.meta.url)),
  plugins: [tailwindcss()],
  resolve: {
    dedupe: ["react", "react-dom"],
    alias: {
      "@": webRoot,
      "@yak-ops/yak-ui": fileURLToPath(
        new URL("../../packages/yak-ui/src/index.ts", import.meta.url),
      ),
    },
  },
  optimizeDeps: {
    // Pre-bundle both JSX entry points with the shared React runtime on cold starts.
    include: ["react", "react-dom/client", "react/jsx-runtime", "react/jsx-dev-runtime"],
  },
  server: {
    fs: {
      allow: [workspaceRoot],
    },
    proxy: {
      "/api": backendUrl,
      "/yak-ops": {
        target: yakOpsBackendUrl,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: fileURLToPath(new URL("../../dist", import.meta.url)),
    emptyOutDir: true,
  },
});
