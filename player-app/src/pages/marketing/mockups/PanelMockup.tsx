/** Agenda de ejemplo: canchas × horarios, para mostrar cómo se ve el panel. */
const COURTS = ['Cancha 1', 'Cancha 2', 'Cancha 3'];
const HOURS = ['18:00', '19:30', '21:00', '22:30'];
const OCCUPIED = new Set([
  'Cancha 1-18:00',
  'Cancha 1-19:30',
  'Cancha 2-18:00',
  'Cancha 3-21:00',
]);
const FIXED = 'Cancha 2-19:30';
const FREE_HIGHLIGHT = 'Cancha 3-19:30';

/** Miniatura de la agenda del club: qué ve el dueño en su panel. */
export function PanelMockup() {
  return (
    <div
      aria-hidden
      className="w-full max-w-full overflow-hidden rounded-2xl border border-cal/10 bg-vidrio p-5 [box-shadow:var(--shadow-card)]"
    >
      <p className="eyebrow text-ink-mute">Agenda de hoy</p>
      <div className="mt-4 grid grid-cols-[auto_repeat(4,1fr)] gap-1.5">
        <div />
        {HOURS.map((hour) => (
          <div key={hour} className="eyebrow py-1 text-center text-ink-mute">
            {hour}
          </div>
        ))}
        {COURTS.map((court) => (
          <CourtRow key={court} court={court} />
        ))}
      </div>
    </div>
  );
}

function CourtRow({ court }: { court: string }) {
  return (
    <>
      <div className="eyebrow flex items-center pr-2 text-ink-soft">{court}</div>
      {HOURS.map((hour) => {
        const key = `${court}-${hour}`;
        const fixed = key === FIXED;
        const freeHighlight = key === FREE_HIGHLIGHT;
        const occupied = OCCUPIED.has(key);
        return (
          <div
            key={hour}
            className={`grid h-8 place-items-center rounded-md text-[0.5rem] font-bold uppercase tracking-wide ${
              fixed
                ? 'bg-ladrillo/20 text-ladrillo-claro'
                : freeHighlight
                  ? 'border border-ladrillo/40 bg-ladrillo/[0.06] text-ladrillo-claro'
                  : occupied
                    ? 'bg-cal/[0.06]'
                    : 'border border-cal/10'
            }`}
          >
            {fixed ? 'Fijo' : freeHighlight ? 'Libre' : ''}
          </div>
        );
      })}
    </>
  );
}
