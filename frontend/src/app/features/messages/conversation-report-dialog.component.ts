import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild, HostListener, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ConversationReportReason } from '../../core/models/api.models';

@Component({
  selector: 'mm-conversation-report-dialog',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './conversation-report-dialog.component.html',
  styleUrl: './conversation-report-dialog.component.css'
})
export class ConversationReportDialogComponent implements AfterViewInit, OnDestroy {
  @Input() targetName = '';
  @Input() title = 'Zgłoś użytkownika';
  @Input() description = '';
  @Input() showReasons = true;
  @Input() detailsRequired = false;
  @Input() submitLabel = 'Wyślij zgłoszenie';
  @Input() busy = false;
  @Input() error = '';
  @Output() cancel = new EventEmitter<void>();
  @Output() submitReport = new EventEmitter<{ reason: ConversationReportReason; details: string }>();
  @ViewChild('dialog') private dialog?: ElementRef<HTMLElement>;
  private readonly previousFocus = document.activeElement as HTMLElement | null;
  @ViewChild('firstReason') private firstReason?: ElementRef<HTMLInputElement>;

  protected reason: ConversationReportReason | '' = '';
  protected details = '';

  ngAfterViewInit(): void {
    setTimeout(() => (this.firstReason?.nativeElement ?? this.dialog?.nativeElement.querySelector<HTMLElement>('textarea'))?.focus());
  }

  protected onBackdropClick(event: MouseEvent): void {
    if (!this.busy && event.target === event.currentTarget) this.cancel.emit();
  }

  protected submit(): void {
    if (this.busy || (this.showReasons && !this.reason) || (this.detailsRequired && !this.details.trim())) return;
    this.submitReport.emit({ reason: this.reason || 'SCAM', details: this.details.trim() });
  }
  ngOnDestroy(): void { if (this.previousFocus?.isConnected) this.previousFocus.focus(); }
  @HostListener('document:keydown', ['$event'])
  protected keydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') { if (!this.busy) this.cancel.emit(); event.preventDefault(); return; }
    if (event.key !== 'Tab') return;
    const elements = Array.from(this.dialog?.nativeElement.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),textarea:not(:disabled),a[href]') ?? []);
    const first=elements[0], last=elements.at(-1);
    if(event.shiftKey && document.activeElement === first) {last?.focus();event.preventDefault();}
    else if(!event.shiftKey && document.activeElement === last) {first?.focus();event.preventDefault();}
  }

}
