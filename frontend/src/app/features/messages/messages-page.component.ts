import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnDestroy, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ChatMessageResponse, ConversationResponse, ListingResponse, UserResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { ConversationService } from '../../core/services/conversation.service';
import { ListingService } from '../../core/services/listing.service';
import { formatListingPrice } from '../../core/utils/price-format';

@Component({
  selector: 'mm-messages-page',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './messages-page.component.html',
  styleUrl: './messages-page.component.css'
})
export class MessagesPageComponent implements OnDestroy {
  @ViewChild('messageList') private messageList?: ElementRef<HTMLElement>;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly conversationsService = inject(ConversationService);
  private readonly listingsService = inject(ListingService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refreshTimer: ReturnType<typeof setInterval>;

  protected currentUser: UserResponse | null = null;
  protected conversations: ConversationResponse[] = [];
  protected intermediaryRequests: ConversationResponse[] = [];
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
  protected hasMore = false;
  protected nextOlderPage = 2;
  protected error = '';
  protected sendError = '';

  constructor() {
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(user => this.currentUser = user);
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.error = '';
      this.sendError = '';
      this.draft = '';
      this.messages = [];
      this.selected = null;
      this.newListing = null;
      this.selectedId = this.parseId(params.get('conversationId'));
      this.newListingId = this.parseId(params.get('listingId'));
      if (this.selectedId) this.loadConversation(this.selectedId);
      if (this.newListingId) this.loadNewListing(this.newListingId);
      this.loadInbox();
    });
    this.refreshTimer = setInterval(() => {
      this.loadInbox(true);
      if (this.selectedId) this.refreshMessages(this.selectedId);
    }, 12000);
  }

  ngOnDestroy(): void {
    clearInterval(this.refreshTimer);
  }

  protected get canSend(): boolean {
    return !!this.draft.trim() && this.draft.trim().length <= 2000 && !this.sending
      && (!!this.selected || (!!this.newListing && this.newListing.sellerId !== this.currentUser?.id));
  }

  protected get isAdmin(): boolean {
    return this.currentUser?.role === 'admin';
  }

  protected otherParticipant(conversation: ConversationResponse): string {
    if (this.currentUser?.id === conversation.buyerId) return `Sprzedający #${conversation.sellerId}`;
    if (this.currentUser?.id === conversation.sellerId) return `Kupujący #${conversation.buyerId}`;
    return `Kupujący #${conversation.buyerId} · Sprzedający #${conversation.sellerId}`;
  }

  protected senderLabel(message: ChatMessageResponse): string {
    if (message.senderId === this.currentUser?.id) return 'Ty';
    if (message.senderRole === 'seller') return 'Sprzedający';
    if (message.senderRole === 'buyer') return 'Kupujący';
    return 'Pośrednik';
  }

  protected priceLabel(listing: ListingResponse): string {
    return formatListingPrice(listing.price, listing.currency.name);
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
    this.conversationsService.messages(conversationId, this.nextOlderPage)
      .pipe(finalize(() => this.loadingOlder = false))
      .subscribe({
        next: page => {
          if (this.selectedId !== conversationId) return;
          this.mergeMessages(page.content);
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

  protected joinAsIntermediary(conversation: ConversationResponse): void {
    if (this.intermediaryBusy) return;
    this.intermediaryBusy = true;
    this.conversationsService.joinAsIntermediary(conversation.id)
      .pipe(finalize(() => this.intermediaryBusy = false))
      .subscribe({
        next: () => void this.router.navigate(['/messages', conversation.id]),
        error: response => this.error = response.error?.message || 'Nie udało się dołączyć do rozmowy.'
      });
  }

  private loadInbox(silent = false): void {
    if (!silent) this.loadingInbox = true;
    this.conversationsService.list().subscribe({
      next: conversations => {
        this.conversations = conversations;
        this.loadingInbox = false;
        if (this.error === 'Nie udało się wczytać rozmów.') this.error = '';
        if (this.selectedId) {
          const updated = conversations.find(conversation => conversation.id === this.selectedId);
          if (updated) this.selected = updated;
        }
        if (this.newListingId && this.currentUser) {
          const existing = conversations.find(conversation =>
            conversation.listingId === this.newListingId && conversation.buyerId === this.currentUser?.id);
          if (existing) void this.router.navigate(['/messages', existing.id], { replaceUrl: true });
        }
      },
      error: () => {
        this.loadingInbox = false;
        if (!silent) this.error = 'Nie udało się wczytać rozmów.';
      }
    });
    if (this.isAdmin) {
      this.conversationsService.intermediaryRequests().subscribe({
        next: requests => this.intermediaryRequests = requests,
        error: () => { if (!silent) this.error = 'Nie udało się wczytać próśb o pośrednika.'; }
      });
    }
  }

  private loadNewListing(listingId: number): void {
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
    this.conversationsService.get(conversationId).subscribe({
      next: conversation => {
        if (this.selectedId !== conversationId) return;
        this.selected = conversation;
        this.conversationsService.messages(conversationId).subscribe({
          next: page => {
            if (this.selectedId !== conversationId) return;
            this.messages = page.content.reverse();
            this.hasMore = !page.last;
            this.nextOlderPage = 2;
            this.loadingMessages = false;
            this.scrollToBottom();
            this.loadInbox(true);
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
    this.conversationsService.messages(conversationId).subscribe({
      next: page => {
        if (this.selectedId !== conversationId) return;
        this.mergeMessages(page.content);
        if (nearBottom) this.scrollToBottom();
      },
      error: () => { }
    });
  }

  private mergeMessages(incoming: ChatMessageResponse[]): void {
    const byId = new Map(this.messages.map(message => [message.id, message]));
    incoming.forEach(message => byId.set(message.id, message));
    this.messages = [...byId.values()].sort((a, b) => a.id - b.id);
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const container = this.messageList?.nativeElement;
      if (container) container.scrollTop = container.scrollHeight;
    });
  }

  private parseId(value: string | null): number | null {
    if (!value) return null;
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }
}
