import { environment } from '../../../environments/environment';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, exhaustMap, filter, finalize, timer } from 'rxjs';

export interface CatalogStatus {
  status: 'IDLE' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  startedAt: string | null;
  finishedAt: string | null;
  pages: number;
  added: number;
  updated: number;
  total: number;
  error: string | null;
}

@Component({
  selector: 'mm-catalog-admin',
  standalone: true,
  imports: [DatePipe],
  template: `
    <section class="catalog" aria-labelledby="catalog-title">
      <div>
        <h2 id="catalog-title">Katalog przedmiotów</h2>
        <p>
          Pobierz nowe przedmioty z MargoWorld. Aktualizacja działa w tle, bez restartu serwisu.
        </p>
      </div>
      <button
        type="button"
        (click)="start()"
        [disabled]="busy() || !state() || state()?.status === 'RUNNING'"
      >
        {{ state()?.status === 'RUNNING' ? 'Aktualizowanie…' : 'Aktualizuj katalog' }}
      </button>
      <div aria-live="polite" role="status">
        @if (error()) {
          <p class="error">{{ error() }}</p>
        }
        @if (state(); as current) {
          @switch (current.status) {
            @case ('IDLE') {
              <p>Katalog jest gotowy do aktualizacji.</p>
            }
            @case ('RUNNING') {
              <p class="progress">Pobieranie katalogu… Pobrane strony: {{ current.pages }}.</p>
            }
            @case ('SUCCEEDED') {
              <p class="success">Katalog został zaktualizowany.</p>
              <dl>
                <div>
                  <dt>Nowe przedmioty</dt>
                  <dd>{{ current.added }}</dd>
                </div>
                <div>
                  <dt>Zaktualizowane</dt>
                  <dd>{{ current.updated }}</dd>
                </div>
                <div>
                  <dt>Pobrane łącznie</dt>
                  <dd>{{ current.total }}</dd>
                </div>
              </dl>
            }
            @case ('FAILED') {
              <p class="error">{{ current.error }}</p>
            }
          }
          @if (current.finishedAt) {
            <p class="date">Ostatnia próba: {{ current.finishedAt | date: 'dd.MM.yyyy HH:mm' }}</p>
          }
        } @else {
          <p>Wczytywanie statusu…</p>
        }
      </div>
    </section>
  `,
  styles: [
    `
      .catalog {
        padding: 1.5rem;
        border: 1px solid rgba(151, 121, 87, 0.42);
        border-radius: 12px;
        background: #261c17;
        color: #f1ede4;
      }
      h2 {
        margin: 0 0 0.6rem;
      }
      p {
        line-height: 1.6;
        color: #b9ad9f;
      }
      button {
        padding: 0.8rem 1.2rem;
        background: #31542b;
        color: #f1ede4;
        border: 1px solid #729661;
        border-radius: 7px;
        font: inherit;
        font-weight: 700;
        cursor: pointer;
      }
      button:disabled {
        opacity: 0.6;
        cursor: default;
      }
      button:focus-visible {
        outline: 2px solid #b4ce93;
        outline-offset: 3px;
      }
      dl {
        display: flex;
        flex-wrap: wrap;
        gap: 2rem;
      }
      dt {
        color: #b9ad9f;
      }
      dd {
        margin: 0.5rem 0;
        font-size: 1.5rem;
        font-weight: 700;
      }
      .success,
      .progress {
        color: #b4ce93;
      }
      .error {
        background: #522820;
        color: #ffdad2;
        border-radius: 7px;
        padding: 0.75rem;
      }
      .date {
        font-size: 0.875rem;
      }
    `,
  ],
})
export class CatalogAdminComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiUrl + '/admin/catalog';
  private readonly destroyRef = inject(DestroyRef);
  protected readonly state = signal<CatalogStatus | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void {
    timer(0, 3000)
      .pipe(
        filter(() => !this.state() || this.state()?.status === 'RUNNING' || !!this.error()),
        exhaustMap(() =>
          this.http.get<CatalogStatus>(this.url).pipe(
            // Keep polling after a temporary network failure.
            // The last known status remains visible until a fresh response arrives.
            catchError(() => {
              this.error.set('Nie udało się odczytać statusu. Ponawiam połączenie…');
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((status) => {
        this.state.set(status);
        this.error.set('');
      });
  }

  protected start(): void {
    if (this.busy() || this.state()?.status === 'RUNNING') return;
    this.busy.set(true);
    this.error.set('');
    this.http
      .post<CatalogStatus>(this.url, {})
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (status) => this.state.set(status),
        error: () =>
          this.error.set(
            'Nie udało się uruchomić aktualizacji. Sprawdź status i spróbuj ponownie.',
          ),
      });
  }
}
