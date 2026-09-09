import type { Amenity } from '../../api/client';

const GLYPHS: Record<string, string> = {
  court: '▦',
  parking: '🅿',
  racket: '✚',
  shower: '~',
  timer: '◷',
  cafe: '☕',
  star: '★',
};

export function AmenityIcon({ icon }: { icon: string }) {
  return <span aria-hidden>{GLYPHS[icon] ?? '✓'}</span>;
}

/**
 * Servicios del club, armados desde las amenities configuradas en el panel.
 */
export function ServicesSection({ amenities }: { amenities: Amenity[] }) {
  if (amenities.length === 0) {
    return null;
  }

  return (
    <section className="mt-10">
      <h2 className="text-2xl">El club te ofrece</h2>
      <div className="mt-4 grid grid-cols-2 gap-2 md:grid-cols-4 md:gap-3">
        {amenities.map((amenity) => (
          <div
            key={amenity.title}
            className="rounded-2xl border border-cal/10 bg-vidrio p-4 text-center"
          >
            <span className="mx-auto flex size-10 items-center justify-center rounded-full bg-cal/[0.06] text-xl text-ladrillo-claro">
              <AmenityIcon icon={amenity.icon} />
            </span>
            <p className="display mt-3 text-base tracking-[0.06em]">{amenity.title}</p>
            {amenity.description && (
              <p className="mt-1 text-xs leading-snug text-ink-soft">{amenity.description}</p>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}
