import type { Slot } from '../../api/client';
import type { ClubInstagram } from '../../components/Ui';
import { HeroClassic } from './HeroClassic';
import { HeroCourtSplit } from './HeroCourtSplit';
import { HeroScoreboard } from './HeroScoreboard';

/**
 * Portada del club: el club elige el diseño desde el panel (Perfil → Diseño
 * de portada) y este componente solo despacha al que corresponda. Los tres
 * reciben el mismo bloque de datos del club — así agregar un diseño nuevo no
 * toca a ClubPage, solo suma un caso acá.
 *
 * <p>Los horarios de hoy (todaySlots y sus callbacks): la clásica y el marcador
 * los ofrecen como fichas para reservar directo; la cancha partida solo muestra
 * el resumen, sin botones.
 *
 * <p>El Instagram del club va en los tres, cada uno con el estilo del diseño.
 */
export function HeroSection({
  name,
  tagline,
  heroImageUrl,
  heroHeadline,
  heroCtaLabel,
  heroOverlay,
  heroVariant,
  address,
  instagram,
  courtCount,
  todaySlots,
  timeZone,
  playersPerCourt,
  onPickSlot,
  onSeeToday,
}: {
  name: string;
  tagline: string | null;
  heroImageUrl: string | null;
  heroHeadline: string | null;
  heroCtaLabel: string | null;
  heroOverlay: number;
  heroVariant: 'CLASSIC' | 'SCOREBOARD' | 'COURT_SPLIT';
  address: string | null;
  instagram: ClubInstagram | null;
  courtCount: number;
  todaySlots: Slot[] | null;
  timeZone: string;
  playersPerCourt: number;
  onPickSlot: (slot: Slot) => void;
  onSeeToday: () => void;
}) {
  if (heroVariant === 'SCOREBOARD') {
    return (
      <HeroScoreboard
        name={name}
        tagline={tagline}
        heroImageUrl={heroImageUrl}
        heroHeadline={heroHeadline}
        heroCtaLabel={heroCtaLabel}
        address={address}
        instagram={instagram}
        courtCount={courtCount}
        todaySlots={todaySlots}
        timeZone={timeZone}
        playersPerCourt={playersPerCourt}
        onPickSlot={onPickSlot}
        onSeeToday={onSeeToday}
      />
    );
  }

  if (heroVariant === 'COURT_SPLIT') {
    return (
      <HeroCourtSplit
        name={name}
        tagline={tagline}
        heroImageUrl={heroImageUrl}
        heroHeadline={heroHeadline}
        heroCtaLabel={heroCtaLabel}
        address={address}
        instagram={instagram}
        courtCount={courtCount}
        todaySlots={todaySlots}
        timeZone={timeZone}
      />
    );
  }

  return (
    <HeroClassic
      name={name}
      tagline={tagline}
      heroImageUrl={heroImageUrl}
      heroHeadline={heroHeadline}
      heroCtaLabel={heroCtaLabel}
      heroOverlay={heroOverlay}
      address={address}
      instagram={instagram}
      courtCount={courtCount}
      todaySlots={todaySlots}
      timeZone={timeZone}
      playersPerCourt={playersPerCourt}
      onPickSlot={onPickSlot}
      onSeeToday={onSeeToday}
    />
  );
}
