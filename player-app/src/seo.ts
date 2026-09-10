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
