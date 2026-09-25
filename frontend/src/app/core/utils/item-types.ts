import { LookupResponse } from '../models/api.models';

const ITEM_TYPE_OPTIONS = [
  ['Jednoręczne', 'Jednoręczne'],
  ['Dwuręczne', 'Dwuręczne'],
  ['Półtoraręczne', 'Półtoraręczne'],
  ['Tarcza', 'Tarcze'],
  ['Dystansowe', 'Dystansowe'],
  ['Pomocnicze', 'Pomocnicze'],
  ['Różdżki', 'Różdżki'],
  ['Orby', 'Orby'],
  ['Strzały', 'Strzały'],
  ['Zbroja', 'Zbroje'],
  ['Hełm', 'Hełmy'],
  ['Buty', 'Buty'],
  ['Rękawice', 'Rękawice'],
  ['Pierścień', 'Pierścienie'],
  ['Naszyjnik', 'Naszyjniki'],
  ['Talizmany', 'Talizmany'],
  ['Konsumpcyjne', 'Konsumpcyjne'],
  ['Waluta', 'Waluta'],
  ['Torby', 'Torby']
] as const;

export function marketItemTypes(types: LookupResponse[]): LookupResponse[] {
  return ITEM_TYPE_OPTIONS.flatMap(([name, label]) => {
    const type = types.find((entry) => entry.name === name);
    return type ? [{ ...type, name: label }] : [];
  });
}

export function isSkrytkaName(name: string): boolean {
  return name.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().includes('skrytk');
}

export function hasEnhancementLevel(typeName: string, itemName: string): boolean {
  return !isSkrytkaName(itemName)
    && !['Konsumpcyjne', 'Talizmany', 'Waluta', 'Torby'].includes(typeName);
}

export function canBindItem(typeName: string): boolean {
  return !['Konsumpcyjne', 'Waluta'].includes(typeName);
}
