import { WorldType } from '../models/api.models';
import { goldMillionsInput, parseGoldMillions, parsePln } from './gold-format';

export const WORLD_TYPE_LABELS: Record<WorldType, string> = {
  TRANSFERABLE: 'Publiczny z przenosinami',
  ISOLATED: 'Publiczny bez przenosin',
  PRIVATE: 'Prywatny / specjalny'
};

/** "18" for 18 000 000 gold per PLN; empty when no rate is set. */
export function goldRateInput(goldPerPln: number | null): string {
  return goldPerPln ? goldMillionsInput(goldPerPln) : '';
}

/** Gold units per PLN from "18", "18m" or "0,05g"; undefined for an invalid value, null for an empty one. */
export function parseGoldRate(input: string): number | null | undefined {
  if (!input.trim()) return null;
  return parseGoldMillions(input) ?? undefined;
}

/** "150" or "0,5" for an amount in grosze; empty when no price is set. */
export function plnInput(grosze: number | null): string {
  if (!grosze) return '';
  const whole = Math.floor(grosze / 100);
  const fraction = String(grosze % 100).padStart(2, '0').replace(/0+$/, '');
  return fraction ? `${whole},${fraction}` : String(whole);
}

/** Grosze from "150" or "0,5"; undefined for an invalid value, null for an empty one. */
export function parsePlnInput(input: string): number | null | undefined {
  if (!input.trim()) return null;
  return parsePln(input.replace(/\s+/g, '').replace(/zł$/i, '')) ?? undefined;
}
