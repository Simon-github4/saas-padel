/*
 * Service worker de la app del jugador (la PWA que se instala en el celular).
 *
 * No guarda la app en cache: cada vez que se abre se pide al servidor, asi un
 * deploy llega al instante y nadie queda con una version vieja. Lo unico que
 * hace es que, si el celular no tiene señal, en vez del dinosaurio del navegador
 * se vea un aviso en castellano con un boton para reintentar. El aviso va
 * entero adentro de este archivo (la lupa incluida): sin señal no se puede
 * pedir nada mas.
 *
 * Vive en la raiz para que su alcance cubra /buscar, /club/... y el resto. Por
 * eso ignora a proposito todo lo que no es de la app del jugador: el panel del
 * club (/admin), la API y el gimnasio (que tiene su propio service worker en /gym/).
 */

const AJENAS = ['/admin', '/api', '/gym', '/gym-app', '/VAADIN'];

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.mode !== 'navigate') {
    return;
  }
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || AJENAS.some((prefijo) => url.pathname.startsWith(prefijo))) {
    return;
  }
  event.respondWith(fetch(request).catch(() => sinConexion()));
});

function sinConexion() {
  const html = `<!doctype html>
<html lang="es-AR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="theme-color" content="#0a0a0a">
<title>Sin conexión</title>
<style>
  body { margin: 0; min-height: 100dvh; display: grid; place-items: center; background: #0a0a0a;
         color: #fff; font: 18px/1.5 system-ui, sans-serif; text-align: center; padding: 24px; box-sizing: border-box; }
  svg { width: 88px; height: 88px; }
  h1 { font-size: 26px; margin: 20px 0 8px; }
  p { color: #a0a0a0; margin: 0 auto 28px; max-width: 320px; }
  button { font: inherit; font-weight: 700; border: 0; border-radius: 999px; padding: 16px 32px;
           background: #ea580c; color: #fff; }
</style>
</head>
<body>
<main>
  <svg viewBox="3.4 3.4 87 87" aria-hidden="true">
    <defs><clipPath id="pelota"><circle cx="40" cy="40" r="24"/></clipPath></defs>
    <g fill="#f5a67b" transform="rotate(45 40 40)">
      <rect x="72" y="37.25" width="12" height="5.5"/>
      <rect x="82" y="35.2" width="25.5" height="9.6" rx="3.2"/>
    </g>
    <circle cx="40" cy="40" r="31.85" fill="none" stroke="#f5a67b" stroke-width="8.3"/>
    <circle cx="40" cy="40" r="24" fill="#ea580c"/>
    <g clip-path="url(#pelota)" fill="none" stroke="#fff4ec" stroke-width="3.6">
      <circle cx="64" cy="16" r="22.8"/>
      <circle cx="16" cy="64" r="22.8"/>
    </g>
  </svg>
  <h1>No hay conexión</h1>
  <p>Revisá que el celular tenga datos o wifi y volvé a intentar.</p>
  <button onclick="location.reload()">Reintentar</button>
</main>
</body>
</html>`;
  return new Response(html, { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } });
}
