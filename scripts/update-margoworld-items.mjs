// node scripts\update-margoworld-items.mjs

import { writeFile } from 'node:fs/promises';

const OUTPUT_PATH = new URL('../src/main/resources/data/margoworld-market-items.json', import.meta.url);
const BASE_URL = 'https://margoworld.pl';
const ICON_BASE_URL = 'https://micc.garmory-cdn.cloud/obrazki/itemy/';
const PAGE_SIZE = 224;

const EQUIPMENT_CLASSES = new Map([
  [1, 'Jednoręczne'],
  [2, 'Dwuręczne'],
  [3, 'Półtoraręczne'],
  [4, 'Dystansowe'],
  [5, 'Pomocnicze'],
  [6, 'Różdżki'],
  [7, 'Orby'],
  [8, 'Zbroja'],
  [9, 'Hełm'],
  [10, 'Buty'],
  [11, 'Rękawice'],
  [12, 'Pierścień'],
  [13, 'Naszyjnik'],
  [14, 'Tarcza'],
  [22, 'Talizmany'],
  [29, 'Strzały']
]);

const EXTRA_CLASSES = new Map([
  [15, 'Neutralne'],
  [16, 'Konsumpcyjne']
]);

const RARITY_NAMES = new Map([
  ['heroic', 'Heroiczny'],
  ['legendary', 'Legendarny'],
  ['upgraded', 'Ulepszony'],
  ['unique', 'Unikatowy'],
  ['common', 'Zwykły']
]);

const BLOCKED_STATS = new Set([
  'permbound',
  'force_binding',
  'noauction',
  'soulbound'
]);

function htmlDecode(value) {
  return value
    .replace(/&quot;/g, '"')
    .replace(/&#039;/g, "'")
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&#(\d+);/g, (_, code) => String.fromCharCode(Number(code)));
}

function normalizeQuery(url) {
  return url.includes('?') ? `${url}&` : `${url}?`;
}

async function fetchText(url) {
  const response = await fetch(url, {
    headers: {
      'User-Agent': 'MargoMarket catalog updater'
    }
  });

  if (!response.ok) {
    throw new Error(`Request failed ${response.status}: ${url}`);
  }

  return response.text();
}

function extractTotalCount(html) {
  const match = html.match(/<center>\s*(\d+) przedmiotów spełnia kryteria/);
  return match ? Number(match[1]) : 0;
}

function parseStats(stat) {
  const entries = new Map();
  const flags = new Set();

  for (const part of stat.split(';')) {
    const trimmed = part.trim();
    if (!trimmed) {
      continue;
    }

    const separatorIndex = trimmed.indexOf('=');
    if (separatorIndex === -1) {
      flags.add(trimmed);
      continue;
    }

    entries.set(trimmed.slice(0, separatorIndex), trimmed.slice(separatorIndex + 1));
  }

  return { entries, flags };
}

function hasBlockedStat(stat) {
  const { entries, flags } = parseStats(stat);
  return [...BLOCKED_STATS].some((key) => flags.has(key) || entries.has(key));
}

function hasBindingOnEquip(stat) {
  return parseStats(stat).flags.has('binds');
}

function descriptionFrom(stat) {
  const { entries } = parseStats(stat);
  return entries.get('opis')?.replaceAll('[br]', '\n').trim() || null;
}

function levelFrom(stat, name) {
  const { entries } = parseStats(stat);
  const statLevel = Number.parseInt(entries.get('lvl') || '', 10);
  if (Number.isInteger(statLevel) && statLevel > 0) {
    return statLevel;
  }

  const rangeLevel = `${name} ${stat}`.match(/\b(20|101|201)\s*-\s*(100|200|300)\b/);
  if (rangeLevel) {
    return Number(rangeLevel[1]);
  }

  return 1;
}

function rarityFrom(stat) {
  const { entries } = parseStats(stat);
  return entries.get('rarity') || null;
}

function shouldInclude(tip) {
  const stat = tip.stat || '';
  const rarity = rarityFrom(stat);
  const level = levelFrom(stat, tip.name);
  const isSkrytka = tip.name.toLocaleLowerCase('pl-PL').includes('skrytka');

  if (rarity !== 'legendary') {
    return false;
  }

  if (hasBlockedStat(stat)) {
    return false;
  }

  if (isSkrytka) {
    return true;
  }

  if (EQUIPMENT_CLASSES.has(tip.cl)) {
    return level > 20 && hasBindingOnEquip(stat);
  }

  if (tip.cl === 15) {
    return level > 1 && hasBindingOnEquip(stat);
  }

  return false;
}

function itemTypeName(tip) {
  return EQUIPMENT_CLASSES.get(tip.cl) || EXTRA_CLASSES.get(tip.cl) || 'Konsumpcyjne';
}

function parseItems(html) {
  const items = [];
  const regex = /<a\s+href="([^"]+)"[^>]*>\s*<span class="margonem_item"[^>]*data-itemtip='([^']+)'/g;
  let match;

  while ((match = regex.exec(html)) !== null) {
    const tip = JSON.parse(htmlDecode(match[2]));
    if (!shouldInclude(tip)) {
      continue;
    }

    const rarity = rarityFrom(tip.stat || '');
    items.push({
      externalId: tip.id,
      name: tip.name,
      iconUrl: `${ICON_BASE_URL}${tip.icon}`,
      level: levelFrom(tip.stat || '', tip.name),
      itemTypeName: itemTypeName(tip),
      rarityName: RARITY_NAMES.get(rarity) || 'Zwykły',
      description: descriptionFrom(tip.stat || ''),
      stats: tip.stat || null,
      source: 'margoworld',
      sourceUrl: `${BASE_URL}${match[1]}`
    });
  }

  return items;
}

async function collectPath(pathAndQuery) {
  const firstUrl = `${BASE_URL}${pathAndQuery}`;
  const firstHtml = await fetchText(firstUrl);
  const total = extractTotalCount(firstHtml);
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const results = parseItems(firstHtml);

  for (let page = 2; page <= pages; page += 1) {
    const separator = normalizeQuery(pathAndQuery);
    const html = await fetchText(`${BASE_URL}${separator}p=${page}`);
    results.push(...parseItems(html));
  }

  return results;
}

const paths = [
  ...[...EQUIPMENT_CLASSES.keys()].map((classId) => `/item/*,${classId}?legendary=on`),
  '/item/*,15?legendary=on',
  '/item?name=skrytka'
];

const byExternalId = new Map();

for (const path of paths) {
  const items = await collectPath(path);
  for (const item of items) {
    byExternalId.set(item.externalId, item);
  }
  console.log(`${path}: ${items.length}`);
}

const catalog = [...byExternalId.values()].sort((a, b) => (
  a.level - b.level ||
  a.name.localeCompare(b.name, 'pl-PL') ||
  a.externalId - b.externalId
));

await writeFile(OUTPUT_PATH, `${JSON.stringify(catalog, null, 2)}\n`, 'utf8');
console.log(`Saved ${catalog.length} items to ${OUTPUT_PATH.pathname}`);
