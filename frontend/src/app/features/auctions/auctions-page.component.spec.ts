import { describe, expect, it, vi } from 'vitest';
import { AuctionsPageComponent } from './auctions-page.component';

describe('auctions page rows', () => {
  function click(target: HTMLElement) {
    const navigate = vi.fn(() => Promise.resolve(true));
    (AuctionsPageComponent.prototype as any).openRow.call({ router: { navigate } }, 5, { target } as unknown as MouseEvent);
    return navigate;
  }

  it('opens the auction when the row is clicked', () => {
    const cell = document.createElement('td');
    cell.textContent = '+3';
    expect(click(cell)).toHaveBeenCalledWith(['/auctions', 5]);
  });

  it('leaves links and buttons inside the row to themselves', () => {
    const link = document.createElement('a');
    const icon = document.createElement('span');
    link.appendChild(icon);
    expect(click(icon)).not.toHaveBeenCalled();
    expect(click(document.createElement('button'))).not.toHaveBeenCalled();
  });
});
