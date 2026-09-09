import { HeroClassic } from './HeroClassic';
import { HeroCourtSplit } from './HeroCourtSplit';
import { HeroScoreboard } from './HeroScoreboard';

/**
 * Portada del club: el club elige el diseño desde el panel (Perfil → Diseño
 * de portada) y este componente solo despacha al que corresponda. Los tres
 * reciben el mismo bloque de datos del club — así agregar un diseño nuevo no
 * toca a ClubPage, solo suma un caso acá.
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
  courtCount,
}: {
  name: string;
  tagline: string | null;
  heroImageUrl: string | null;
  heroHeadline: string | null;
  heroCtaLabel: string | null;
  heroOverlay: number;
  heroVariant: 'CLASSIC' | 'SCOREBOARD' | 'COURT_SPLIT';
  address: string | null;
  courtCount: number;
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
        courtCount={courtCount}
      />
    );
  }

  if (heroVariant === 'COURT_SPLIT') {
    return (
      <HeroCourtSplit
        name={name}
        heroImageUrl={heroImageUrl}
        heroHeadline={heroHeadline}
        heroCtaLabel={heroCtaLabel}
        address={address}
        courtCount={courtCount}
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
      courtCount={courtCount}
    />
  );
}
