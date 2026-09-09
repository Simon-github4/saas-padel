import { Badge } from '../../../components/Ui';
import { perPerson } from '../../../format';

/** Horarios de ejemplo: no vienen de ningún club real. */
const SLOTS = [
  { time: '18:00', price: 9000, promo: false },
  { time: '19:30', price: 9000, promo: true },
  { time: '21:00', price: 11000, promo: false },
  { time: '22:30', price: 11000, promo: false },
];

/** Grilla de horarios como la ve el jugador al abrir el link de un club. */
export function PhoneMockup() {
  return (
    <div
      aria-hidden
      className="mx-auto w-full max-w-[280px] overflow-hidden rounded-[2rem] border border-cal/10 bg-vidrio p-5 [box-shadow:var(--shadow-card)]"
    >
      <p className="eyebrow text-ink-mute">Club Necochea Pádel</p>
      <p className="display mt-1 text-lg tracking-[0.06em]">Hoy, martes 1</p>
      <div className="mt-4 grid grid-cols-2 gap-2">
        {SLOTS.map((slot, index) => (
          <div
            key={slot.time}
            style={{ animationDelay: `${index * 90}ms` }}
            className={`ficha-in rounded-xl border p-3 ${
              slot.promo ? 'border-ladrillo/40 bg-ladrillo/[0.06]' : 'border-cal/10 bg-pista'
            }`}
          >
            <div className="flex items-start justify-between gap-1">
              <span className="display text-xl tabular-nums">{slot.time}</span>
              {slot.promo && <Badge tone="promo">Promo</Badge>}
            </div>
            <p
              className={`mt-1 text-xs font-bold tabular-nums ${
                slot.promo ? 'text-ladrillo-claro' : 'text-cal'
              }`}
            >
              {perPerson(slot.price, 4)} <span className="font-normal text-ink-soft">c/u</span>
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
