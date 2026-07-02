import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Built bundle is served same-origin by Spring Boot under /admin (from
// classpath:/static/admin). In dev, Vite proxies /api to the app on :8085 so the
// browser stays same-origin for the session cookie + CSRF.
export default defineConfig({
  base: "/admin/",
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static/admin",
    emptyOutDir: true,
    // The antd ecosystem (antd + rc-* primitives + @ant-design icons) and
    // Refine are deeply co-dependent and import each other cyclically. They
    // MUST stay in one chunk: splitting them apart produces circular chunks,
    // which Rollup warns about and which crash the SPA at boot (a module is
    // referenced before its chunk finishes initializing). react and tiptap
    // are independent of that cycle, so they split cleanly. This combined
    // vendor chunk is ~1.7 MB (~480 kB gzip); the limit reflects that.
    chunkSizeWarningLimit: 1800,
    rollupOptions: {
      output: {
        // Split only vendor libraries that do NOT participate in the
        // antd<->rc-*<->@ant-design<->refine cycle. Function form matches by
        // path prefix so subpath-only packages (e.g. @tiptap/pm/*) group too.
        manualChunks(id) {
          if (!id.includes("node_modules")) return undefined;
          if (/[\\/]node_modules[\\/]@tiptap[\\/]/.test(id)) return "tiptap";
          if (
            /[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/.test(
              id,
            )
          )
            return "react";
          if (
            /[\\/]node_modules[\\/](antd|rc-[^\\/]+|@ant-design|@refinedev)[\\/]/.test(
              id,
            )
          )
            return "vendor-antd";
          return undefined;
        },
      },
    },
  },
  server: {
    proxy: {
      "/api": "http://localhost:8085",
    },
  },
});
