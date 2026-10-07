import { describe, expect, it } from 'vitest';
import type { BookingHistoryItem } from './api/client';
import { isPlayed, periodBuckets, periodRange, periodStart, summarize } from './stats';

/**
 * Las barras del gráfico de actividad.
 *
 * <p>Es el módulo con aritmética de fechas de verdad de la app, y esa aritmética
 * se equivoca en silencio: un turno mal ubicado no rompe nada, sólo aparece en
 * el día de al lado.
 *
 * <p>Sobre el horario de verano: los límites de cada barra se arman con los
 * componentes de la fecha local y no sumando milisegundos, justamente para que
 * el día en que el reloj se corre no desplace las barras. Eso se prueba acá
 * como invariante -- toda barra de día empieza a la medianoche local -- y no
 * simulando un cambio de hora, que exigiría fijar la zona horaria del proceso:
 * Argentina hoy no tiene horario de verano, así que el runner no lo ejercita
 * solo. Si la construcción volviera a ser "sumar 24 horas", en un cambio de
 * hora alguna barra empezaría a las 23 o a la 1, y ese invariante cae.
 */

/** Un jueves, para que la semana de referencia tenga días antes y después. */
const AHORA = new Date(2026, 8, 10, 15, 0); // 10 de septiembre de 2026, 15:00 local

function turno(empieza: Date, minutos = 90, status = 'COMPLETED'): BookingHistoryItem {
  const termina = new Date(empieza.getTime() + minutos * 60 * 1000);
  return {
    bookingId: `t-${empieza.toISOString()}`,
    clubName: 'Pádel Necochea',
    clubSlug: 'club-necochea',
    courtName: 'Cancha 1',
    startTime: empieza.toISOString(),
    endTime: termina.toISOString(),
    status,
    totalPrice: 28000,
    paidAmount: 0,
    managementToken: 'tok',
  };
}

describe('isPlayed', () => {
  it('cuenta el turno terminado', () => {
    expect(isPlayed(turno(new Date(2026, 8, 9, 20, 0), 90, 'COMPLETED'), AHORA)).toBe(true);
  });

  it('cuenta el confirmado que ya pasó, aunque nadie lo haya cerrado', () => {
    expect(isPlayed(turno(new Date(2026, 8, 9, 20, 0), 90, 'CONFIRMED'), AHORA)).toBe(true);
  });

  it('no cuenta el confirmado que todavía no se jugó', () => {
    expect(isPlayed(turno(new Date(2026, 8, 12, 20, 0), 90, 'CONFIRMED'), AHORA)).toBe(false);
  });

  it('no cuenta el que el club cerró al cobrarlo antes del partido', () => {
    expect(isPlayed(turno(new Date(2026, 8, 10, 18, 0), 90, 'COMPLETED'), AHORA)).toBe(false);
  });

  it('no cuenta el cancelado ni el que quedó pendiente', () => {
    expect(isPlayed(turno(new Date(2026, 8, 9, 20, 0), 90, 'CANCELLED'), AHORA)).toBe(false);
    expect(isPlayed(turno(new Date(2026, 8, 9, 20, 0), 90, 'AWAITING_CONFIRMATION'), AHORA))
      .toBe(false);
  });
});

describe('periodStart', () => {
  it('los últimos 7 días arrancan hace seis días, a la medianoche', () => {
    // Hoy jueves 10 cuenta como uno de los siete: arranca el viernes 4.
    expect(periodStart('week', AHORA)).toEqual(new Date(2026, 8, 4));
  });

  it('el último mes son los últimos 30 días, hoy incluido', () => {
    expect(periodStart('month', AHORA)).toEqual(new Date(2026, 7, 12));
  });

  it('cruza el fin de mes y de año sin problema', () => {
    expect(periodStart('week', new Date(2027, 0, 3, 10, 0))).toEqual(new Date(2026, 11, 28));
  });

  it('arranca el año el primero de enero', () => {
    expect(periodStart('year', AHORA)).toEqual(new Date(2026, 0, 1));
  });

  it('no tiene inicio para el histórico', () => {
    expect(periodStart('all', AHORA)).toBeNull();
  });
});

describe('periodRange', () => {
  it('termina hoy y arranca donde arranca la ventana', () => {
    expect(periodRange([], 'week', AHORA)).toEqual({
      from: new Date(2026, 8, 4),
      to: new Date(2026, 8, 10),
    });
  });

  it('el histórico arranca el día del primer turno jugado', () => {
    const history = [turno(new Date(2025, 4, 20, 21, 0)), turno(new Date(2024, 2, 5, 20, 0))];
    expect(periodRange(history, 'all', AHORA)).toEqual({
      from: new Date(2024, 2, 5),
      to: new Date(2026, 8, 10),
    });
  });

  it('el histórico sin nada jugado no tiene rango', () => {
    expect(periodRange([], 'all', AHORA)).toBeNull();
  });
});

describe('barras', () => {
  it('los últimos 7 días terminan hoy, con la inicial de cada día', () => {
    // Del viernes 4 al jueves 10.
    const buckets = periodBuckets([], 'week', AHORA);
    expect(buckets.map((bucket) => bucket.label)).toEqual(['V', 'S', 'D', 'L', 'M', 'M', 'J']);
  });

  it('el último mes tiene una barra por día, con rótulo solo los lunes', () => {
    const buckets = periodBuckets([], 'month', AHORA);
    expect(buckets).toHaveLength(30);
    // Del miércoles 12 de agosto al jueves 10 de septiembre: lunes 17, 24, 31, 7.
    expect(buckets.map((bucket) => bucket.label).filter(Boolean)).toEqual(['17', '24', '31', '7']);
    expect(buckets[buckets.length - 1].start).toEqual(new Date(2026, 8, 10));
  });

  it('el año tiene doce meses', () => {
    expect(periodBuckets([], 'year', AHORA)).toHaveLength(12);
  });

  it('toda barra de día empieza a la medianoche local', () => {
    // El invariante del horario de verano: si los límites se armaran sumando
    // milisegundos, el día del cambio de hora empezaría a las 23 o a la 1.
    for (const bucket of periodBuckets([], 'week', AHORA)) {
      expect(bucket.start.getHours()).toBe(0);
      expect(bucket.start.getMinutes()).toBe(0);
    }
  });

  it('las barras son contiguas: donde termina una empieza la siguiente', () => {
    // Sin esto, un turno podría caer en un hueco entre dos barras y desaparecer
    // del gráfico sin que nada avise.
    const buckets = periodBuckets([], 'month', AHORA);
    for (let i = 1; i < buckets.length; i++) {
      expect(buckets[i].start.getTime()).toBe(buckets[i - 1].end.getTime());
    }
  });

  it('ubica cada turno en su día', () => {
    const buckets = periodBuckets(
      [
        turno(new Date(2026, 8, 7, 20, 0)), // lunes
        turno(new Date(2026, 8, 9, 21, 30)), // miércoles
        turno(new Date(2026, 8, 9, 8, 0)), // miércoles, otra vez
      ],
      'week',
      AHORA,
    );

    // Barras del viernes 4 al jueves 10.
    expect(buckets.map((bucket) => bucket.turnos)).toEqual([0, 0, 0, 1, 0, 2, 0]);
  });

  it('el turno que arranca justo en el límite cae en el día que empieza', () => {
    // start es inclusivo y end exclusivo, así que dos barras vecinas no se
    // pelean por el turno de la medianoche.
    const buckets = periodBuckets([turno(new Date(2026, 8, 9, 0, 0))], 'week', AHORA);
    expect(buckets[5].turnos).toBe(1); // miércoles 9
    expect(buckets[4].turnos).toBe(0); // martes 8
  });

  it('no cuenta los turnos que no se jugaron', () => {
    const buckets = periodBuckets(
      [
        turno(new Date(2026, 8, 7, 20, 0), 90, 'CANCELLED'),
        turno(new Date(2026, 8, 12, 20, 0), 90, 'CONFIRMED'),
      ],
      'week',
      AHORA,
    );
    expect(buckets.every((bucket) => bucket.turnos === 0)).toBe(true);
  });

  it('el histórico abarca desde el primer año jugado hasta hoy', () => {
    const buckets = periodBuckets([turno(new Date(2024, 2, 5, 20, 0))], 'all', AHORA);
    expect(buckets.map((bucket) => bucket.label)).toEqual(['2024', '2025', '2026']);
    expect(buckets[0].turnos).toBe(1);
  });

  it('el histórico sin nada jugado no dibuja barras', () => {
    expect(periodBuckets([], 'all', AHORA)).toEqual([]);
  });
});

describe('totales', () => {
  it('salen de sumar las mismas barras que dibuja el gráfico', () => {
    // Con dos caminos de cálculo, tarde o temprano el número grande y las
    // barras terminan diciendo cosas distintas.
    const history = [
      turno(new Date(2026, 8, 7, 20, 0), 90),
      turno(new Date(2026, 8, 9, 20, 0), 60),
    ];

    const { turnos, minutos, buckets } = summarize(history, 'week', AHORA);

    expect(turnos).toBe(2);
    expect(minutos).toBe(150);
    expect(buckets.reduce((total, bucket) => total + bucket.turnos, 0)).toBe(turnos);
    expect(buckets.reduce((total, bucket) => total + bucket.minutos, 0)).toBe(minutos);
  });

  it('deja fuera lo que cae afuera de la ventana', () => {
    const haceOchoDias = turno(new Date(2026, 8, 2, 20, 0));
    expect(summarize([haceOchoDias], 'week', AHORA).turnos).toBe(0);
    expect(summarize([haceOchoDias], 'month', AHORA).turnos).toBe(1);
  });

  it('el último mes cuenta lo del mes calendario anterior si cae en los 30 días', () => {
    // Con "este mes" de calendario, el 10 de septiembre esto quedaba afuera.
    const finDeAgosto = turno(new Date(2026, 7, 28, 20, 0));
    expect(summarize([finDeAgosto], 'month', AHORA).turnos).toBe(1);
  });
});
