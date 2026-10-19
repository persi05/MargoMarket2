import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged, finalize, Subject } from 'rxjs';
import { BlockedWord, BlockedWordRequest, PageResponse } from '../../core/models/api.models';
import { AdminService } from '../../core/services/admin.service';
import { PaginationComponent } from '../../shared/pagination/pagination.component';

@Component({
  selector: 'mm-moderation-panel',
  standalone: true,
  imports: [ReactiveFormsModule, PaginationComponent],
  templateUrl: './moderation-panel.component.html',
  styleUrl: './moderation-panel.component.css'
})
export class ModerationPanelComponent {
  private readonly admin = inject(AdminService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    term: ['', [Validators.required, Validators.maxLength(80),
      (control: AbstractControl) => [...String(control.value).replace(/\s/g, '')].length >= 3 ? null : { minimumCharacters: true }]],
    matchMode: ['EXACT' as BlockedWordRequest['matchMode']],
    enabled: [true]
  });
  protected words: BlockedWord[] = [];
  protected pageData: PageResponse<BlockedWord> | null = null;
  protected currentPage = 1;
  protected loading = true;
  protected busy = false;
  protected error = '';
  protected notice = '';
  protected search = '';
  private readonly searchChanges = new Subject<string>();
  protected editingId: number | undefined;

  constructor() {
    this.searchChanges.pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.currentPage = 1;
        this.load();
      });
    this.load();
  }

  protected onSearch(value: string): void {
    this.search = value;
    this.searchChanges.next(value);
  }

  protected changePage(page: number): void {
    if (this.loading || page < 1 || page === this.currentPage) return;
    this.currentPage = page;
    this.load();
  }

  protected load(): void {
    this.loading = true;
    this.error = '';
    const search = this.search.trim().toLocaleLowerCase('pl-PL')
      .normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/ł/g, 'l');
    this.admin.blockedWords(this.currentPage, search)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading = false)).subscribe({
      next: page => {
        if (page.totalElements > 0 && this.currentPage > page.totalPages) {
          this.currentPage = page.totalPages;
          this.load();
          return;
        }
        this.pageData = page;
        this.words = page.content;
      },
      error: () => this.error = 'Nie udało się pobrać listy. Spróbuj ponownie.'
    });
  }

  protected edit(word: BlockedWord): void {
    this.editingId = word.id;
    this.form.setValue({ term: word.term, matchMode: word.matchMode, enabled: word.enabled });
    this.notice = '';
    this.error = '';
  }

  protected cancel(): void {
    this.editingId = undefined;
    this.form.reset({ term: '', matchMode: 'EXACT', enabled: true });
  }

  protected save(): void {
    if (this.form.invalid || this.busy || this.loading) return;
    this.persist(this.form.getRawValue(), this.editingId, true);
  }

  protected toggle(word: BlockedWord): void {
    this.persist({ term: word.term, matchMode: word.matchMode, enabled: !word.enabled }, word.id);
  }

  private persist(request: BlockedWordRequest, id?: number, reset = false): void {
    if (this.busy) return;
    this.busy = true;
    this.error = '';
    this.notice = '';
    this.admin.saveBlockedWord(request, id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy = false)).subscribe({
      next: word => {
        if (reset) this.cancel();
        else if (this.editingId === word.id) this.edit(word);
        this.notice = 'Zapisano. Reguła będzie stosowana przy wysyłaniu wiadomości.';
        this.load();
      },
      error: response => this.error = response.error?.message || 'Nie udało się zapisać wpisu.'
    });
  }

  protected remove(word: BlockedWord): void {
    if (this.busy || !confirm(`Usunąć „${word.term}” z listy zakazanych słów?`)) return;
    this.busy = true;
    this.error = '';
    this.notice = '';
    this.admin.deleteBlockedWord(word.id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.busy = false)).subscribe({
      next: () => {
        if (this.words.length === 1 && this.currentPage > 1) this.currentPage--;
        if (this.editingId === word.id) this.cancel();
        this.notice = 'Usunięto wpis z listy.';
        this.load();
      },
      error: () => this.error = 'Nie udało się usunąć wpisu.'
    });
  }
}
