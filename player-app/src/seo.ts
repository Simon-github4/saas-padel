/**
 * Título y meta-descripción por pantalla, para una SPA sin server-side
 * rendering.
 *
 * <p>Google sí ejecuta el JS antes de indexar, pero en una segunda pasada,
 * más lenta -- y usa lo que encuentre en el título y la descripción para el
 * resultado de búsqueda. Sin esto, todas las páginas (todos los clubes)
 * comparten el mismo título genérico de index.html, y no hay forma de
 * distinguirlas en un resultado de Google.
 */
export function setPageMeta(title: string, description?: string): () => void {
  const previousTitle = document.title;
  document.title = title;

  let meta: HTMLMetaElement | null = null;
  let previousDescription: string | null = null;
  if (description) {
    meta = document.querySelector('meta[name="description"]');
    if (meta) {
      previousDescription = meta.getAttribute('content');
      meta.setAttribute('content', description);
    }
  }

  return () => {
    document.title = previousTitle;
    if (meta && previousDescription !== null) {
      meta.setAttribute('content', previousDescription);
    }
  };
}

/**
 * Datos estructurados (JSON-LD): le dicen a Google explícitamente que esta
 * página es un club de pádel concreto, con esta dirección y este teléfono,
 * en vez de que tenga que adivinarlo leyendo el texto -- es lo que habilita
 * un resultado con mapa o ficha, no solo el link pelado.
 */
export function setStructuredData(data: Record<string, unknown>): () => void {
  const script = document.createElement('script');
  script.type = 'application/ld+json';
  script.textContent = JSON.stringify(data);
  document.head.appendChild(script);
  return () => script.remove();
}
