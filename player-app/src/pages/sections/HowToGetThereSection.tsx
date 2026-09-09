import { useEffect, useState } from 'react';

/**
 * Cómo llegar: dirección, previsualización del mapa y botón a Google Maps.
 *
 * <p>El mapa no arranca cargado: recien aparece 1,5s despues de entrar a la
 * pagina, para no meter el iframe de OpenStreetMap en el primer pintado. El
 * que quiere ir antes de esos 1,5s puede tocar el bloque para adelantarlo.
 *
 * <p>La previsualización necesita coordenadas. Sin ellas el bloque queda como
 * estaba, con la dirección y el botón: el club las carga desde el panel.
 */
export function HowToGetThereSection({
  address,
  city,
  mapsUrl,
  latitude,
  longitude,
}: {
  address: string | null;
  city: string | null;
  mapsUrl: string | null;
  latitude: number | null;
  longitude: number | null;
}) {
  const [showMap, setShowMap] = useState(false);
  const hasPin = latitude != null && longitude != null;

  useEffect(() => {
    if (!hasPin) {
      return;
    }
    const timer = setTimeout(() => setShowMap(true), 1500);
    return () => clearTimeout(timer);
  }, [hasPin]);

  const fullAddress = [address, city].filter(Boolean).join(', ');
  if (!fullAddress && !mapsUrl) {
    return null;
  }

  return (
    <section className="mt-10">
      <h2 className="flex items-center gap-3 text-2xl">
        <span className="grid size-7 shrink-0 place-items-center rounded-full bg-cal/[0.06] text-ladrillo-claro">
          <PinGlyph />
        </span>
        Cómo llegar
      </h2>

      {fullAddress && <p className="mt-4 text-lg text-ink-soft">{fullAddress}</p>}

      {hasPin && (
        <div className="mt-4 overflow-hidden rounded-2xl border border-cal/10 bg-vidrio">
          {showMap ? (
            <iframe
              title="Mapa con la ubicación del club"
              className="block h-64 w-full border-0"
              loading="lazy"
              referrerPolicy="no-referrer"
              src={embedUrl(latitude, longitude)}
            />
          ) : (
            <button
              type="button"
              onClick={() => setShowMap(true)}
              className="group grid h-64 w-full place-items-center bg-[radial-gradient(70%_70%_at_50%_50%,rgba(234,88,12,0.14),transparent_70%)] px-6 text-center transition hover:bg-vidrio-alto"
            >
              <span>
                <span className="mx-auto grid size-12 place-items-center rounded-full bg-cal/[0.06] text-ladrillo-claro transition group-hover:bg-cal/10">
                  <PinGlyph className="size-6" />
                </span>
                <span className="eyebrow mt-4 block text-cal">Cargando el mapa…</span>
                <span className="mt-2 block text-xs text-ink-mute">
                  Desde OpenStreetMap, o tocá para verlo ya
                </span>
              </span>
            </button>
          )}
        </div>
      )}

      {mapsUrl && (
        <a
          href={mapsUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-full border border-cal/10 bg-vidrio px-5 py-3.5 text-sm font-bold uppercase tracking-[0.12em] text-cal transition hover:border-cal/25 hover:bg-vidrio-alto"
        >
          Abrir en Google Maps
          <span aria-hidden>→</span>
        </a>
      )}
    </section>
  );
}

/**
 * Recuadro del mapa alrededor del club.
 *
 * <p>OpenStreetMap embebe por bbox, no por zoom: el margen fija cuánta cuadra se
 * ve. 0.004 grados son unos 400 metros, suficiente para reconocer las esquinas
 * de alrededor sin perder de vista el pin.
 */
function embedUrl(lat: number, lon: number): string {
  const margin = 0.004;
  const bbox = [lon - margin, lat - margin, lon + margin, lat + margin].join(',');
  return (
    'https://www.openstreetmap.org/export/embed.html' +
    `?bbox=${bbox}&layer=mapnik&marker=${lat},${lon}`
  );
}

function PinGlyph({ className = 'size-4' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className={className}
      aria-hidden
    >
      <path d="M12 21s-7-5.5-7-11a7 7 0 1 1 14 0c0 5.5-7 11-7 11Z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  );
}
