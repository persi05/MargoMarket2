export interface ItemStatLine {
  label: string;
  value: string;
  accent?: boolean;
}

const STAT_LABELS: Record<string, string> = {
  dmg: 'Atak',
  ac: 'Pancerz',
  acdmg: 'Niszczy',
  act: 'Odporność na truciznę',
  dz: 'Zręczność',
  ds: 'Siła',
  di: 'Intelekt',
  str: 'Siła',
  agi: 'Szybkość',
  hp: 'Życie',
  hpbon: 'Życie za 1 pkt siły',
  mana: 'Mana',
  energybon: 'Energia',
  manabon: 'Mana',
  enfatig: 'Podczas obrony',
  manafatig: 'Podczas obrony',
  afterheal: 'Po walce',
  sa: 'Szybkość ataku',
  crit: 'Cios krytyczny',
  critval: 'Moc ciosu krytycznego fizycznego',
  critmval: 'Siła krytyka magicznego',
  lowcrit: 'Obniża szansę na cios krytyczny przeciwnika',
  allstats: 'Wszystkie cechy',
  da: 'Wszystkie cechy',
  evade: 'Unik',
  lowevade: 'Obniża unik przeciwnika',
  capacity: 'Pojemność',
  absorb: 'Absorbuje do',
  absorbm: 'Absorbuje do',
  adest: 'Obniża właścicielowi',
  resdmg: 'Niszczy odporności magiczne',
  pierceb: 'Szansa na zablokowanie przebicia',
  respred: 'Przyśpiesza wracanie do siebie',
  blok: 'Blok',
  heal: 'Przywraca życie',
  resfire: 'Odporność na ogień',
  resfrost: 'Odporność na zimno',
  reslight: 'Odporność na błyskawice',
  pierce: 'Przebicie pancerza',
  contra: 'Kontra',
  poison: 'Trucizna',
  slow: 'Obniża SA przeciwnika',
  dmgmulphysical: 'Obrażenia fizyczne',
  dmgmulabsolute: 'Obrażenia absolutne',
  dmgmullight: 'Obrażenia od błyskawic',
  dmgmulfrost: 'Obrażenia od zimna',
  dmgmulfire: 'Obrażenia od ognia',
  dmgmulpoison: 'Obrażenia od trucizny',
  dmgmulwound: 'Obrażenia od głębokiej rany'
};

const PERCENT_STATS = new Set([
  'crit',
  'critval',
  'critmval',
  'lowcrit',
  'resfire',
  'resfrost',
  'reslight',
  'pierce',
  'contra',
  'act',
  'pierceb',
  'respred',
  'dmgmulphysical',
  'dmgmulabsolute',
  'dmgmullight',
  'dmgmulfrost',
  'dmgmulfire',
  'dmgmulpoison',
  'dmgmulwound'
]);

const DECIMAL_STATS = new Set([
  'sa',
  'slow'
]);

const PROFESSION_LABELS: Record<string, string> = {
  w: 'Wojownik',
  p: 'Paladyn',
  b: 'Tancerz ostrzy',
  m: 'Mag',
  h: 'Łowca',
  t: 'Tropiciel'
};

function entries(stats: string | null | undefined): Map<string, string> {
  const result = new Map<string, string>();
  if (!stats) {
    return result;
  }

  stats.split(';').forEach((part) => {
    const trimmed = part.trim();
    const index = trimmed.indexOf('=');
    if (index > 0) {
      result.set(trimmed.slice(0, index), trimmed.slice(index + 1));
    }
  });

  return result;
}

function formatValue(key: string, value: string): string {
  const normalized = value.replace(',', '-');
  if (key === 'acdmg') {
    return `${value} punktów pancerza podczas ciosu`;
  }
  if (key === 'absorb') {
    return `${value} obrażeń fizycznych`;
  }
  if (key === 'absorbm') {
    return `${value} obrażeń magicznych`;
  }
  if (key === 'adest') {
    return `${value} punktów przywracania życia podczas walki`;
  }
  if (key === 'resdmg') {
    return `o ${value}% podczas ciosu`;
  }
  if (key === 'lowevade') {
    return `o ${value} podczas ataku`;
  }
  if (key === 'lowcrit') {
    return `o ${value} punktów procentowych podczas obrony`;
  }
  if (key === 'hpbon') {
    return `+${value}`;
  }
  if (key === 'enfatig' || key === 'manafatig') {
    const [chance, amount] = value.split(',');
    return `${chance}% szansy na utratę ${amount} ${key === 'enfatig' ? 'energii' : 'many'} przez przeciwnika`;
  }
  if (key === 'afterheal') {
    const [chance, amount] = value.split(',');
    return `${chance}% szansy na przywrócenie do ${amount} punktów życia`;
  }
  if (DECIMAL_STATS.has(key)) {
    const numeric = Number.parseInt(normalized, 10);
    if (Number.isInteger(numeric)) {
      return `${numeric < 0 ? '-' : '+'}${Math.abs(numeric / 100)}`;
    }
  }

  if (key === 'heal') {
    return `${normalized} punktów życia podczas walki`;
  }

  const signed = normalized.startsWith('-') ? normalized : `+${normalized}`;
  return PERCENT_STATS.has(key) ? `${signed}%` : signed;
}

export function itemStatLines(stats: string | null | undefined): ItemStatLine[] {
  const parsed = entries(stats);
  const lines: ItemStatLine[] = [];

  parsed.forEach((value, key) => {
    const label = STAT_LABELS[key];
    if (!label) {
      return;
    }

    lines.push({
      label,
      value: key === 'dmg' || key === 'ac' ? value.replace(',', '-') : formatValue(key, value),
      accent: true
    });
  });

  const legendaryBonus = parsed.get('legbon')?.split(',')[0];
  const legendaryDescriptions: Record<string, ItemStatLine> = {
    anguish: { label: 'Krwawa udręka', value: '8% szansy, że trafienie wywoła krwawienie na pięć tur' },
    cleanse: { label: 'Płomienne oczyszczenie', value: '12% szansy na usunięcie negatywnych efektów po otrzymaniu celnego ataku' },
    critred: { label: 'Krytyczna osłona', value: 'otrzymywane ciosy krytyczne są słabsze o 25%' },
    curse: { label: 'Klątwa', value: '9% szansy, że trafienie zablokuje najbliższą akcję przeciwnika' },
    facade: { label: 'Fasada opieki', value: 'otrzymywane ciosy są słabsze o 13%' },
    glare: { label: 'Oślepienie', value: '9% szansy na zablokowanie najbliższej akcji atakującego' },
    holytouch: { label: 'Dotyk anioła', value: '7% szansy na regenerację 6% życia przez trzy tury po ataku' },
    lastheal: { label: 'Ostatni ratunek', value: 'jednorazowe leczenie, gdy po otrzymaniu obrażeń życie spadnie poniżej 18%' },
    puncture: { label: 'Przeszywająca skuteczność', value: 'zdolności defensywne celu ataku są obniżone o 12%' },
    verycrit: { label: 'Cios bardzo krytyczny', value: '17% szansy na zwiększenie mocy ciosu krytycznego o 75%' }
  };
  if (legendaryBonus && legendaryDescriptions[legendaryBonus]) {
    lines.push({ ...legendaryDescriptions[legendaryBonus], accent: true });
  }

  return lines;
}

export function itemRequiredProfessions(stats: string | null | undefined): string | null {
  const required = entries(stats).get('reqp');
  return required ? [...required].map((key) => PROFESSION_LABELS[key] || key).join(', ') : null;
}

export function itemLastAvailableDuring(stats: string | null | undefined): string | null {
  return entries(stats).get('etiquette')?.split('|').pop()?.trim() || null;
}
