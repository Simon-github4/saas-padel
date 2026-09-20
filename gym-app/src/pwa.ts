export function registerGymServiceWorker() {
  if (!('serviceWorker' in navigator)) {
    return;
  }

  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/gym/sw.js', { scope: '/gym/' }).catch(() => {
      // La app sigue funcionando aunque el navegador no permita registrar la PWA.
    });
  });
}
