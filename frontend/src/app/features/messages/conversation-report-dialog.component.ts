import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ConversationReportReason } from '../../core/models/api.models';

@Component({
  selector: 'mm-conversation-report-dialog',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './conversation-report-dialog.component.html'
})
export class ConversationReportDialogComponent implements AfterViewInit {
  @Input({ required: true }) targetName = '';
  @Input() busy = false;
  @Input() error = '';
  @Output() cancel = new EventEmitter<void>();
  @Output() submitReport = new EventEmitter<{ reason: ConversationReportReason; details: string }>();
  @ViewChild('firstReason') private firstReason?: ElementRef<HTMLInputElement>;

  protected reason: ConversationReportReason | '' = '';
  protected details = '';

  ngAfterViewInit(): void {
    setTimeout(() => this.firstReason?.nativeElement.focus());
  }

  protected onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.cancel.emit();
  }

  protected submit(): void {
    if (this.busy || !this.reason) return;
    this.submitReport.emit({ reason: this.reason, details: this.details.trim() });
  }
}
