import { formatGold, formatPln } from './gold-format';

export function auctionAmount(amount: number | null, currency: string): string {
  if (amount === null) return 'Zaloguj się, aby zobaczyć kwotę';
  return currency === 'PLN' ? formatPln(amount) : formatGold(amount);
}
export function auctionRemaining(endsAt: string, serverNow: number): string {
  const seconds = Math.max(0, Math.ceil((Date.parse(endsAt) - serverNow) / 1000));
  if (!seconds) return 'Czas minął';
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor(seconds / 3600) % 24;
  const minutes = Math.floor(seconds / 60) % 60;
  return days ? `${days} d ${hours} godz.` : hours ? `${hours} godz. ${minutes} min` : `${minutes} min ${seconds % 60} s`;
}
export function auctionStatus(status: string): string {
  return ({ACTIVE: 'Trwa licytacja', ENDED_WITH_WINNER: 'Wyłoniono zwycięzcę', ENDED_NO_BIDS: 'Zakończona bez ofert', CANCELLED: 'Anulowana',
    PENDING_CONTACT: 'Oczekuje na kontakt', IN_PROGRESS: 'Uzgadnianie wymiany', DECLARED_COMPLETED: 'Sprzedawca zadeklarował realizację',
    ADMIN_CONFIRMED: 'Wymiana potwierdzona przez administratora', DISPUTED: 'Spór do rozpatrzenia'} as Record<string,string>)[status] ?? status;
}
