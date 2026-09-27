import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { BlockedWord, BlockedWordRequest } from '../../core/models/api.models';
import { AdminService } from '../../core/services/admin.service';

@Component({
  selector: 'mm-moderation-panel',
  standalone: true,
  imports: [ReactiveFormsModule],
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
  protected loading = true;
  protected busy = false;
  protected error = '';
  protected notice = '';
  protected search = '';
  protected editingId: number | undefined;

  constructor() { this.load(); }

  protected get filteredWords(): BlockedWord[] {
    const search = this.search.trim().toLocaleLowerCase('pl').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/ł/g, 'l');
    return this.words.filter(word => word.term.includes(search));
  }

  protected load(): void {
    this.loading = true;
    this.error = '';
    this.admin.blockedWords().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading = false)).subscribe({
      next: words => this.words = words,
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
        this.words = [...this.words.filter(entry => entry.id !== word.id), word].sort((a, b) => a.term.localeCompare(b.term));
        if (reset) this.cancel();
        else if (this.editingId === word.id) this.edit(word);
        this.notice = 'Zapisano. Reguła będzie stosowana przy wysyłaniu wiadomości.';
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
        this.words = this.words.filter(entry => entry.id !== word.id);
        if (this.editingId === word.id) this.cancel();
        this.notice = 'Usunięto wpis z listy.';
      },
      error: () => this.error = 'Nie udało się usunąć wpisu.'
    });
  }
}
