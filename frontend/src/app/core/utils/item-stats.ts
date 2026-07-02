export interface ItemStatLine {
  label: string;
  value: string;
  accent?: boolean;
}

const STAT_LABELS: Record<string, string> = {
  dmg: 'Atak',
  ac: 'Pancerz',
  dz: 'Zręczność',
  str: 'Siła',
  agi: 'Szybkość',
  hp: 'Życie',
  mana: 'Mana',
  sa: 'Szybkość ataku',
  crit: 'Cios krytyczny',
  critval: 'Siła ciosu krytycznego',
  critmval: 'Siła krytyka magicznego',
  lowcrit: 'Cios bardzo krytyczny',
  allstats: 'Wszystkie cechy',
  da: 'Wszystkie cechy',
  evade: 'Unik',
  blok: 'Blok',
  heal: 'Przywraca życie',
  resfire: 'Odporność na ogień',
  resfrost: 'Odporność na zimno',
  reslight: 'Odporność na błyskawice',
  pierce: 'Przebicie pancerza',
  contra: 'Kontra',
  poison: 'Trucizna',
  slow: 'Obniża SA przeciwnika'
};

const PERCENT_STATS = new Set([
  'crit',
  'critval',
  'critmval',
  'lowcrit',
  'evade',
  'resfire',
  'resfrost',
  'reslight',
  'pierce',
  'contra'
]);

const DECIMAL_STATS = new Set([
  'sa',
  'slow'
]);

const HIDDEN_STATS = new Set([
  'amount',
  'canpreview',
  'cansplit',
  'capacity',
  'expire_date',
  'lootbox2',
  'lvl',
  'opis',
  'quest',
  'rarity',
  'reqp',
  'timelimit'
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

function flags(stats: string | null | undefined): Set<string> {
  const result = new Set<string>();
  if (!stats) {
    return result;
  }

  stats.split(';').forEach((part) => {
    const trimmed = part.trim();
    if (trimmed && !trimmed.includes('=')) {
      result.add(trimmed);
    }
  });

  return result;
}

function formatValue(key: string, value: string): string {
  const normalized = value.replace(',', '-');
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
  const parsedFlags = flags(stats);
  const lines: ItemStatLine[] = [];

  if (parsedFlags.has('binds')) {
    lines.push({
      label: 'Wiąże',
      value: 'po założeniu'
    });
  }

  parsed.forEach((value, key) => {
    const label = STAT_LABELS[key];
    if (!label || HIDDEN_STATS.has(key)) {
      return;
    }

    lines.push({
      label,
      value: key === 'dmg' || key === 'ac' ? value.replace(',', '-') : formatValue(key, value),
      accent: true
    });
  });

  const requiredProfessions = parsed.get('reqp');
  if (requiredProfessions) {
    lines.push({
      label: 'Wymagane profesje',
      value: [...requiredProfessions].map((key) => PROFESSION_LABELS[key] || key).join(', ')
    });
  }

  const legendaryBonus = parsed.get('legbon');
  if (legendaryBonus?.startsWith('lastheal')) {
    const threshold = legendaryBonus.split(',')[1] || '18';
    lines.push({
      label: 'Ostatni ratunek',
      value: `jednorazowe zregenerowanie znacznej ilości punktów życia, gdy po otrzymaniu obrażeń życie spadnie poniżej ${threshold}%`
    });
  }

  return lines;
}
