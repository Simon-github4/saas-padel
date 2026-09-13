/**
 * Los mensajes que hoy le llegan al dueño a cualquier hora, pasando de largo.
 *
 * <p>Es el dolor en el idioma en que lo vive: no "gestión de turnos", sino el
 * "¿tenés cancha a las 21?" de las once y media de la noche. La cinta se frena
 * al pasar el mouse, para poder leerla.
 */
const MESSAGES = [
  { text: '¿Tenés cancha hoy a las 21?', time: '23:12' },
  { text: 'Somos 4, ¿qué horario te queda?', time: '23:31' },
  { text: '¿A cuánto está el turno?', time: '23:47' },
  { text: '¿Mañana a la noche hay algo?', time: '00:03' },
  { text: '¿Sigue libre la de las 19:30?', time: '07:02' },
  { text: 'Al final no vamos, ¿la liberás?', time: '08:15' },
  { text: 'Hola, ¿hay lugar el sábado?', time: '14:26' },
];

export function MessagesMarquee() {
  return (
    <section
      aria-label="Mensajes que el link contesta por vos"
      className="border-y border-cal/10 bg-vidrio/40 py-5 md:flex md:items-center"
    >
      <p className="eyebrow mb-4 px-5 text-center text-ink-mute md:mb-0 md:w-52 md:shrink-0 md:pl-8 md:pr-4 md:text-left">
        Lo que el link contesta por vos
      </p>
      <div className="mk-marquee min-w-0 flex-1 overflow-hidden">
        <ul className="mk-marquee-track">
          {[...MESSAGES, ...MESSAGES].map((message, index) => (
            <li
              key={index}
              aria-hidden={index >= MESSAGES.length || undefined}
              className="mr-3 flex shrink-0 items-end gap-3 rounded-2xl rounded-bl-md border border-cal/10 bg-vidrio px-4 py-2.5"
            >
              <span className="whitespace-nowrap text-sm text-arena">{message.text}</span>
              <span className="text-[0.65rem] tabular-nums text-ink-mute">{message.time}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
