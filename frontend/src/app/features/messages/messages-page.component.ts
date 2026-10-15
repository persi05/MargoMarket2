import { DatePipe } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, ElementRef, HostListener, Input, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ChatMessageResponse, ConversationReportReason, ConversationResponse, ListingResponse, UserResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { ConversationService } from '../../core/services/conversation.service';
import { ListingService } from '../../core/services/listing.service';
import { formatListingPrice } from '../../core/utils/price-format';
import { ConversationReportDialogComponent } from './conversation-report-dialog.component';

@Component({
  selector: 'mm-messages-page',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink, ConversationReportDialogComponent],
  templateUrl: './messages-page.component.html',
  styleUrl: './messages-page.component.css'
})
export class MessagesPageComponent implements OnInit, OnDestroy {
  @Input() adminAll = false;
  @Input() adminConversationId: number | null = null;
  @ViewChild('messageList') private messageList?: ElementRef<HTMLElement>;
  @ViewChild('reportTrigger') private reportTrigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('closeDialog') private closeDialog?: ElementRef<HTMLElement>;
  @ViewChild('closeTrigger') private closeTrigger?: ElementRef<HTMLButtonElement>;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly conversationsService = inject(ConversationService);
  private readonly listingsService = inject(ListingService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private refreshTimer?: ReturnType<typeof setInterval>;
  private inboxLoaded = false;
  protected inboxRequestPending = false;
  private inboxRequestVersion = 0;

  protected currentUser: UserResponse | null = null;
  protected conversations: ConversationResponse[] = [];
  protected inboxTab: 'listings' | 'exchanges' = 'listings';
  protected visibleInboxCount = 10;
  protected inboxTotal = 0;
  protected inboxHasMore = false;
  protected selected: ConversationResponse | null = null;
  protected selectedId: number | null = null;
  protected newListing: ListingResponse | null = null;
  protected newListingId: number | null = null;
  protected messages: ChatMessageResponse[] = [];
  protected draft = '';
  protected loadingInbox = true;
  protected loadingMessages = false;
  protected loadingOlder = false;
  protected sending = false;
  protected intermediaryBusy = false;
  protected closeBusy = false;
  protected closeOpen = false;
  protected closeStep: 'confirm' | 'sold' = 'confirm';
  protected closeNotice = '';
  protected contactBusy = false;
  protected contactNotice = '';
  protected reportBusy = false;
  protected reportOpen = false;
  protected reportNotice = '';
  protected hasMore = false;
  protected nextOlderPage = 2;
  protected error = '';
  protected sendError = '';

  constructor() {
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(user => this.currentUser = user);
  }

  ngOnInit(): void {
    if (this.adminAll) {
      this.loadInbox();
      if (this.adminConversationId) this.selectAdminConversation(this.adminConversationId);
    } else {
      this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
        this.error = '';
        this.sendError = '';
        this.draft = '';
        this.messages = [];
        this.selected = null;
        this.reportOpen = false;
        this.reportNotice = '';
        this.closeOpen = false;
        this.closeNotice = '';
        this.contactNotice = '';
        this.newListing = null;
        this.selectedId = this.parseId(params.get('conversationId'));
        this.newListingId = this.parseId(params.get('listingId'));
        if (this.selectedId) this.loadConversation(this.selectedId);
        if (this.newListingId) this.loadNewListing(this.newListingId);
        if (!this.inboxLoaded && !this.inboxRequestPending) this.loadInbox();
      });
    }
    this.refreshTimer = setInterval(() => {
      if (document.hidden) return;
      this.loadInbox(true);
      if (this.selectedId) this.refreshMessages(this.selectedId);
    }, 12000);
  }

  @HostListener('document:visibilitychange')
  protected refreshWhenVisible(): void {
    if (document.hidden) return;
    this.loadInbox(true);
    if (this.selectedId) this.refreshMessages(this.selectedId);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.reportOpen) this.closeReport();
    if (this.closeOpen && !this.closeBusy) this.dismissClose();
  }

  protected get canSend(): boolean {
    return !this.adminAll && !!this.draft.trim() && this.draft.trim().length <= 2000 && !this.sending && !this.selected?.closedAt
      && (!!this.selected || (!!this.newListing && this.newListing.sellerId !== this.currentUser?.id));
  }

  protected get canCloseExchange(): boolean {
    const conversation = this.selected;
    const user = this.currentUser;
    if (this.adminAll || !conversation || conversation.closedAt || !user) return false;
    if (conversation.intermediaryId) {
      return user.role === 'admin' && user.id === conversation.intermediaryId;
    }
    return user.id === conversation.buyerId || user.id === conversation.sellerId;
  }

  protected showMoreConversations(): void {
    if (this.inboxRequestPending || !this.inboxHasMore) return;
    this.visibleInboxCount += 10;
    this.loadInbox(true);
  }

  protected changeInboxTab(tab: 'listings' | 'exchanges'): void {
    if (this.inboxTab === tab) return;
    this.inboxTab = tab;
    this.visibleInboxCount = 10;
    this.conversations = [];
    this.inboxLoaded = false;
    this.loadInbox();
  }

  protected selectAdminConversation(conversationId: number): void {
    if (!this.adminAll) return;
    this.selectedId = conversationId;
    this.selected = null;
    this.messages = [];
    this.error = '';
    this.loadConversation(conversationId);
  }

  protected otherParticipant(conversation: ConversationResponse): string {
    if (this.currentUser?.id === conversation.buyerId) return `Sprzedający: ${conversation.sellerDisplayName}`;
    if (this.currentUser?.id === conversation.sellerId) return `Kupujący: ${conversation.buyerDisplayName}`;
    return `Kupujący: ${conversation.buyerDisplayName} · Sprzedający: ${conversation.sellerDisplayName}`;
  }

  protected senderLabel(message: ChatMessageResponse): string {
    if (message.senderId === this.currentUser?.id) return 'Ty';
    if (message.senderRole === 'system') return 'System';
    const role = message.senderRole === 'seller' ? 'Sprzedający'
      : message.senderRole === 'buyer' ? 'Kupujący' : 'Pośrednik';
    return `${message.senderDisplayName || role} · ${role}`;
  }

  protected priceLabel(listing: ListingResponse): string {
    return formatListingPrice(listing.price, listing.currency.name);
  }

  protected get reportTargetName(): string {
    if (!this.selected) return '';
    return this.currentUser?.id === this.selected.buyerId
      ? this.selected.sellerDisplayName : this.selected.buyerDisplayName;
  }

  protected openReport(): void {
    this.reportNotice = '';
    this.reportOpen = true;
  }

  protected closeReport(): void {
    this.reportOpen = false;
    this.reportNotice = '';
    setTimeout(() => this.reportTrigger?.nativeElement.focus());
  }

  protected send(): void {
    if (!this.canSend) return;
    const body = this.draft.trim();
    this.sending = true;
    this.sendError = '';
    if (this.newListingId) {
      this.conversationsService.start(this.newListingId, body)
        .pipe(finalize(() => this.sending = false))
        .subscribe({
          next: conversation => {
            this.draft = '';
            void this.router.navigate(['/messages', conversation.id]);
          },
          error: response => this.sendError = response.error?.message || 'Nie udało się rozpocząć rozmowy.'
        });
      return;
    }
    if (!this.selectedId) return;
    const conversationId = this.selectedId;
    this.conversationsService.send(conversationId, body)
      .pipe(finalize(() => this.sending = false))
      .subscribe({
        next: message => {
          if (this.selectedId !== conversationId) return;
          this.draft = '';
          this.mergeMessages([message]);
          this.scrollToBottom();
          this.loadInbox(true);
        },
        error: response => this.sendError = response.error?.message || 'Nie udało się wysłać wiadomości.'
      });
  }

  protected onComposerKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return;
    event.preventDefault();
    this.send();
  }

  protected loadOlder(): void {
    if (!this.selectedId || !this.hasMore || this.loadingOlder) return;
    const conversationId = this.selectedId;
    const container = this.messageList?.nativeElement;
    const oldHeight = container?.scrollHeight ?? 0;
    this.loadingOlder = true;
    (this.adminAll
      ? this.conversationsService.adminChatMessages(conversationId, this.nextOlderPage)
      : this.conversationsService.messages(conversationId, this.nextOlderPage))
      .pipe(finalize(() => this.loadingOlder = false))
      .subscribe({
        next: page => {
          if (this.selectedId !== conversationId) return;
          this.mergeMessages(page.content, true);
          this.hasMore = !page.last;
          this.nextOlderPage++;
          setTimeout(() => {
            if (container) container.scrollTop += container.scrollHeight - oldHeight;
          });
        },
        error: () => this.error = 'Nie udało się wczytać starszych wiadomości.'
      });
  }

  protected requestIntermediary(): void {
    if (!this.selected || this.selected.intermediaryStatus !== 'NONE' || this.intermediaryBusy) return;
    const conversationId = this.selected.id;
    this.intermediaryBusy = true;
    this.conversationsService.requestIntermediary(conversationId)
      .pipe(finalize(() => this.intermediaryBusy = false))
      .subscribe({
        next: conversation => {
          if (this.selectedId === conversationId) this.selected = conversation;
          this.refreshMessages(conversationId);
          this.loadInbox(true);
        },
        error: response => this.error = response.error?.message || 'Nie udało się poprosić o pośrednika.'
      });
  }

  protected openClose(): void {
    if (!this.canCloseExchange || this.closeBusy) return;
    this.closeStep = 'confirm';
    this.closeNotice = '';
    this.closeOpen = true;
    setTimeout(() => this.closeDialog?.nativeElement.querySelector<HTMLButtonElement>('.close-yes')?.focus());
  }

  protected dismissClose(): void {
    if (this.closeBusy) return;
    this.closeOpen = false;
    this.closeNotice = '';
    setTimeout(() => this.closeTrigger?.nativeElement.focus());
  }

  protected onCloseBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) this.dismissClose();
  }

  protected confirmClose(): void {
    if (!this.selected) return;
    if (this.currentUser?.id === this.selected.sellerId && this.selected.listingId) {
      this.closeStep = 'sold';
      setTimeout(() => this.closeDialog?.nativeElement.querySelector<HTMLButtonElement>('.close-yes')?.focus());
      return;
    }
    this.finishClose(false);
  }

  protected finishClose(markSold: boolean): void {
    if (!this.canCloseExchange || !this.selected || this.closeBusy) return;
    this.closeBusy = true;
    this.closeNotice = '';
    this.conversationsService.close(this.selected.id, markSold).pipe(finalize(() => this.closeBusy = false)).subscribe({
      next: conversation => {
        this.closeOpen = false;
        this.selected = conversation;
        this.loadInbox(true);
        this.refreshMessages(conversation.id);
      },
      error: response => this.closeNotice = response.error?.message || 'Nie udało się zakończyć wymiany.'
    });
  }

  protected requestContact(): void {
    if (!this.selected?.closedAt || this.contactBusy) return;
    const conversationId = this.selected.id;
    this.contactBusy = true;
    this.contactNotice = '';
    this.conversationsService.requestContact(conversationId)
      .pipe(finalize(() => this.contactBusy = false)).subscribe({
        next: conversation => this.updateContactConversation(conversationId, conversation),
        error: response => this.contactNotice = response.error?.message || 'Nie udało się wysłać prośby o kontakt.'
      });
  }

  protected respondToContact(accept: boolean): void {
    if (!this.selected?.closedAt || this.selected.contactRequestStatus !== 'PENDING' || this.contactBusy) return;
    const conversationId = this.selected.id;
    this.contactBusy = true;
    this.contactNotice = '';
    this.conversationsService.respondToContact(conversationId, accept)
      .pipe(finalize(() => this.contactBusy = false)).subscribe({
        next: conversation => this.updateContactConversation(conversationId, conversation),
        error: response => this.contactNotice = response.error?.message || 'Nie udało się odpowiedzieć na prośbę.'
      });
  }

  private updateContactConversation(conversationId: number, conversation: ConversationResponse): void {
    if (this.selectedId !== conversationId) return;
    this.selected = conversation;
    this.loadInbox(true);
    this.refreshMessages(conversationId);
  }

  protected submitReport(report: { reason: ConversationReportReason; details: string }): void {
    if (!this.selected || this.reportBusy) return;
    this.reportBusy = true;
    this.reportNotice = '';
    this.conversationsService.report(this.selected.id, report.reason, report.details)
      .pipe(finalize(() => this.reportBusy = false)).subscribe({
        next: () => {
          this.closeReport();
          this.reportNotice = 'Zgłoszenie użytkownika przekazano administratorom.';
        },
        error: response => this.reportNotice = response.error?.message || 'Nie udało się wysłać zgłoszenia.'
      });
  }

  private loadInbox(silent = false): void {
    if (!silent) this.loadingInbox = true;
    const requestVersion = ++this.inboxRequestVersion;
    this.inboxRequestPending = true;
    const scope = this.currentUser?.role === 'admin'
      ? this.inboxTab === 'exchanges' ? 'EXCHANGES' : 'LISTINGS' : 'ALL';
    (this.adminAll ? this.conversationsService.adminChatsPage(this.visibleInboxCount)
      : this.conversationsService.inbox(this.visibleInboxCount, scope)).subscribe({
      next: page => {
        if (requestVersion !== this.inboxRequestVersion) return;
        this.inboxRequestPending = false;
        this.inboxLoaded = true;
        this.conversations = page.content;
        this.inboxTotal = page.totalElements;
        this.inboxHasMore = !page.last;
        this.loadingInbox = false;
        if (this.error === 'Nie udało się wczytać rozmów.') this.error = '';
        if (this.selectedId) {
          const updated = page.content.find(conversation => conversation.id === this.selectedId);
          if (updated) this.selected = updated;
        }
      },
      error: () => {
        if (requestVersion !== this.inboxRequestVersion) return;
        this.inboxRequestPending = false;
        this.loadingInbox = false;
        if (!silent) this.error = 'Nie udało się wczytać rozmów.';
      }
    });
  }

  private loadNewListing(listingId: number): void {
    this.conversationsService.forListing(listingId).subscribe({
      next: existing => {
        if (this.newListingId !== listingId) return;
        if (existing) void this.router.navigate(['/messages', existing.id], { replaceUrl: true });
      },
      error: () => { }
    });
    this.listingsService.getOne(listingId).subscribe({
      next: listing => {
        if (this.newListingId !== listingId) return;
        if (listing.sellerId === this.currentUser?.id) {
          this.error = 'Nie możesz napisać do siebie w sprawie własnego ogłoszenia.';
        } else if (listing.status !== 'active') {
          this.error = 'To ogłoszenie nie jest już aktywne.';
        } else {
          this.newListing = listing;
        }
      },
      error: () => this.error = 'Nie udało się wczytać ogłoszenia.'
    });
  }

  private loadConversation(conversationId: number): void {
    this.loadingMessages = true;
    (this.adminAll ? this.conversationsService.adminChat(conversationId)
      : this.conversationsService.get(conversationId)).subscribe({
      next: conversation => {
        if (this.selectedId !== conversationId) return;
        this.selected = conversation;
        if (!this.adminAll && this.currentUser?.role === 'admin') {
          const tab = conversation.intermediaryId === this.currentUser.id ? 'exchanges' : 'listings';
          if (this.inboxTab !== tab) this.changeInboxTab(tab);
        }
        (this.adminAll ? this.conversationsService.adminChatMessages(conversationId)
          : this.conversationsService.messages(conversationId)).subscribe({
          next: page => {
            if (this.selectedId !== conversationId) return;
            this.messages = page.content.reverse();
            this.hasMore = !page.last;
            this.nextOlderPage = 2;
            this.loadingMessages = false;
            const inboxConversation = this.conversations.find(item => item.id === conversationId);
            if (inboxConversation && !this.adminAll) inboxConversation.unreadCount = 0;
            this.changeDetector.detectChanges();
            this.scrollToBottom();
          },
          error: () => {
            this.loadingMessages = false;
            this.error = 'Nie udało się wczytać wiadomości.';
          }
        });
      },
      error: response => {
        this.loadingMessages = false;
        this.error = response.error?.message || 'Nie udało się otworzyć rozmowy.';
      }
    });
  }

  private refreshMessages(conversationId: number): void {
    if (this.loadingMessages || this.loadingOlder) return;
    const container = this.messageList?.nativeElement;
    const nearBottom = !container || container.scrollHeight - container.scrollTop - container.clientHeight < 100;
    (this.adminAll ? this.conversationsService.adminChatMessages(conversationId)
      : this.conversationsService.messages(conversationId)).subscribe({
      next: page => {
        if (this.selectedId !== conversationId) return;
        this.mergeMessages(page.content);
        if (this.nextOlderPage === 2) this.hasMore = !page.last;
        if (nearBottom) this.scrollToBottom();
      },
      error: () => { }
    });
  }

  private mergeMessages(incoming: ChatMessageResponse[], includeOlder = false): void {
    const byId = new Map(this.messages.map(message => [message.id, message]));
    incoming.forEach(message => byId.set(message.id, message));
    const merged = [...byId.values()].sort((a, b) => a.id - b.id);
    if (!includeOlder && this.nextOlderPage === 2 && merged.length > 50) {
      this.messages = merged.slice(-50);
      this.hasMore = true;
    } else {
      this.messages = merged;
    }
  }

  private scrollToBottom(): void {
    const conversationId = this.selectedId;
    const scroll = () => {
      if (this.selectedId !== conversationId) return;
      const container = this.messageList?.nativeElement;
      if (container) container.scrollTop = container.scrollHeight;
    };
    scroll();
    requestAnimationFrame(() => {
      scroll();
      requestAnimationFrame(scroll);
    });
  }

  private parseId(value: string | null): number | null {
    if (!value) return null;
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }
}
