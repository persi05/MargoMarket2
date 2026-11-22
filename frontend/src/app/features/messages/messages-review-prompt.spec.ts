import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { MessagesPageComponent } from './messages-page.component';
import { AuthService } from '../../core/services/auth.service';
import { ConversationService } from '../../core/services/conversation.service';
import { ChatRealtimeService } from '../../core/services/chat-realtime.service';
import { ListingService } from '../../core/services/listing.service';
import { ReviewService } from '../../core/services/review.service';
import { ConversationResponse, PendingReview } from '../../core/models/api.models';

const pending: PendingReview = {confirmationId: 4, conversationId: 30, sellerId: 2, sellerDisplayName: 'Rycerz',
  itemName: 'Miecz', confirmedAt: '2026-11-20T10:00:00Z', reviewableUntil: '2026-12-20T10:00:00Z'};
const conversation = (id: number) => ({id, buyerId: 1, sellerId: 2} as ConversationResponse);

describe('Review prompt in a conversation', () => {
  let user: BehaviorSubject<{id: number} | null>;
  let calls: number;

  function create() {
    user = new BehaviorSubject<{id: number} | null>({id: 1});
    calls = 0;
    TestBed.configureTestingModule({imports: [MessagesPageComponent], providers: [provideRouter([]),
      {provide: ActivatedRoute, useValue: {paramMap: of()}},
      {provide: AuthService, useValue: {user$: user}},
      {provide: ConversationService, useValue: {}},
      {provide: ChatRealtimeService, useValue: {events$: of()}},
      {provide: ListingService, useValue: {}},
      {provide: ReviewService, useValue: {pending: () => { calls++; return of([pending]); }}}]});
    return TestBed.createComponent(MessagesPageComponent).componentInstance as unknown as {
      reviewFor(c: ConversationResponse): PendingReview | null;
    };
  }

  it('offers the buyer a review for the confirmed exchange of this conversation', () => {
    const page = create();
    expect(calls).toBe(1);
    expect(page.reviewFor(conversation(30))).toEqual(pending);
    expect(page.reviewFor(conversation(31))).toBeNull();
  });

  it('does not offer the seller to review themselves', () => {
    const page = create();
    user.next({id: 2});
    expect(page.reviewFor({...conversation(30), buyerId: 1, sellerId: 2})).toBeNull();
  });

  it('reloads pending reviews when another account logs in', () => {
    create();
    user.next({id: 1});
    expect(calls).toBe(1);
    user.next({id: 3});
    expect(calls).toBe(2);
  });
});
