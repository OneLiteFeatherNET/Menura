import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// The server (auth, API, health) runs next to the built UI on port 8080. In dev only, Vite forwards
// those paths to it so the UI can use relative URLs with the same-origin session cookie.
const SERVER = 'http://localhost:8080';

export default defineConfig({
  plugins: [
    vue({
      template: {
        compilerOptions: {
          isCustomElement: (tag) => tag.startsWith('md-'),
        },
      },
    }),
  ],
  server: {
    proxy: {
      '/auth': SERVER,
      '/api': SERVER,
      '/health': SERVER,
    },
  },
});
