import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ command }) => ({
  plugins: [react(), tailwindcss()],
  // En produccion los archivos se piden desde /gym-app/ (el index lo sirve el
  // backend en /gym/<club>): asi no compiten con ninguna ruta de la app, que vive
  // en /gym/. En desarrollo la base es la raiz, porque con otra base Vite no
  // devuelve el index para /gym/<club> y la ruta de la app no abre.
  base: command === 'build' ? '/gym-app/' : '/',
  build: {
    // Directo al classpath de Spring Boot, igual que la app del jugador, pero en
    // una carpeta propia: la del jugador vacia target/classes/static antes de
    // construir (emptyOutDir), asi que esta tiene que correr DESPUES, y solo
    // vacia su propia carpeta. El orden lo fija el pom.xml.
    outDir: '../target/classes/static/gym-app',
    emptyOutDir: true,
  },
  server: {
    // Uno mas que la app del jugador (5174): las dos se pueden levantar a la vez.
    port: 5175,
    strictPort: true,
    // Se sirve en toda la red local para abrirla desde el celular.
    host: true,
    proxy: {
      // La API la sirve Spring.
      '/api': 'http://localhost:8080',
      // Y el manifest de cada club, que es dinamico. El resto de /gym/... lo
      // resuelve Vite (es la propia app).
      '^/gym/[^/]+/manifest\\.webmanifest$': 'http://localhost:8080',
      '^/gym/[^/]+/icon-(180|192|512)\\.png$': 'http://localhost:8080',
      '/gym/sw.js': 'http://localhost:8080',
    },
  },
}));
