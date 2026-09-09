import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    // El build va directo al classpath de Spring Boot, para que el jar sirva la
    // app del jugador sin necesidad de un servidor de estaticos aparte.
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    // 5173 es el puerto que toma cualquier proyecto Vite de la maquina; el
    // 5174 evita pelearlo y hace que el link de desarrollo sea siempre el mismo.
    port: 5174,
    strictPort: true,
    // Se sirve en toda la red local para poder abrir la app desde el celular
    // (p. ej. http://192.168.0.195:5174), no solo desde este navegador.
    host: true,
    proxy: {
      // En desarrollo, Vite sirve la app y delega en Spring todo lo que sea API.
      '/api': 'http://localhost:8080',
    },
  },
});
