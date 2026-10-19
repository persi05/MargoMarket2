package com.margomarket.controller;

import com.margomarket.dto.ChatMessageRequest;
import com.margomarket.dto.ChatMessageResponse;
import com.margomarket.dto.CloseConversationRequest;
import com.margomarket.dto.ConversationResponse;
import com.margomarket.dto.ConversationReportRequest;
import com.margomarket.dto.ConversationReportResponse;
import com.margomarket.dto.PageResponse;
import com.margomarket.dto.StartConversationRequest;
import com.margomarket.mapper.PageMapper;
import com.margomarket.model.User;
import com.margomarket.service.ConversationService;
import com.margomarket.service.ConversationReportService;
import com.margomarket.service.ConversationExchangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService service;
    private final ConversationReportService reportService;
    private final ConversationExchangeService exchangeService;
    private final PageMapper pageMapper;

    @GetMapping
    public List<ConversationResponse> list(@AuthenticationPrincipal User user) {
        return service.list(user);
    }

    @GetMapping("/inbox")
    public PageResponse<ConversationResponse> inbox(@RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "ALL") String scope, @AuthenticationPrincipal User user) {
        return pageMapper.toResponse(service.inbox(user, limit, scope), conversation -> conversation);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", service.unreadCount(user));
    }

    @GetMapping("/admin/chats")
    public List<ConversationResponse> adminChats(@AuthenticationPrincipal User user) {
        return service.adminChats(user);
    }

    @GetMapping("/admin/chats/page")
    public PageResponse<ConversationResponse> adminChatsPage(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal User user) {
        return pageMapper.toResponse(service.adminChatsPage(user, page, limit), conversation -> conversation);
    }

    @GetMapping("/admin/chats/{id}")
    public ConversationResponse adminChat(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.adminChat(id, user);
    }

    @GetMapping("/admin/chats/{id}/messages")
    public PageResponse<ChatMessageResponse> adminChatMessages(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int page, @AuthenticationPrincipal User user) {
        return pageMapper.toResponse(service.adminChatMessages(id, page, user), message -> message);
    }

    @GetMapping("/intermediary-requests")
    public List<ConversationResponse> intermediaryRequests(@AuthenticationPrincipal User user) {
        return exchangeService.intermediaryRequests(user);
    }

    @GetMapping("/intermediary-exchanges")
    public PageResponse<ConversationResponse> intermediaryExchanges(@RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal User user) {
        return pageMapper.toResponse(exchangeService.intermediaryExchangesPage(user, page), conversation -> conversation);
    }

    @GetMapping("/reports")
    public PageResponse<ConversationReportResponse> reports(@RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal User user) {
        return pageMapper.toResponse(reportService.reportsPage(user, page), report -> report);
    }

    @GetMapping("/reports/{id}/messages")
    public List<ChatMessageResponse> reportMessages(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return reportService.reportMessages(id, user);
    }

    @PostMapping("/reports/{id}/review")
    public ConversationReportResponse reviewReport(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return reportService.reviewReport(id, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse start(@Valid @RequestBody StartConversationRequest request,
                                      @AuthenticationPrincipal User user) {
        return service.start(request.listingId(), request.body(), user);
    }

    @GetMapping("/{id}")
    public ConversationResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.get(id, user);
    }

    @GetMapping("/by-listing/{listingId}")
    public ResponseEntity<ConversationResponse> forListing(@PathVariable Long listingId,
            @AuthenticationPrincipal User user) {
        return service.forListing(listingId, user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}/messages")
    public PageResponse<ChatMessageResponse> messages(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @AuthenticationPrincipal User user) {
        return pageMapper.toResponse(service.messages(id, page, user), message -> message);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse send(@PathVariable Long id,
                                     @Valid @RequestBody ChatMessageRequest request,
                                     @AuthenticationPrincipal User user) {
        return service.send(id, request.body(), user);
    }

    @PostMapping("/{id}/intermediary-request")
    public ConversationResponse requestIntermediary(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return exchangeService.requestIntermediary(id, user);
    }

    @PostMapping("/{id}/join-as-intermediary")
    public ConversationResponse joinAsIntermediary(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return exchangeService.joinAsIntermediary(id, user);
    }

    @PostMapping("/{id}/close")
    public ConversationResponse close(@PathVariable Long id,
                                      @RequestBody(required = false) CloseConversationRequest request,
                                      @AuthenticationPrincipal User user) {
        return exchangeService.close(id, user, request != null && request.markSold());
    }

    @PostMapping("/{id}/contact-request")
    public ConversationResponse requestContact(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return exchangeService.requestContact(id, user);
    }

    @PostMapping("/{id}/contact-request/accept")
    public ConversationResponse acceptContact(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return exchangeService.respondToContact(id, user, true);
    }

    @PostMapping("/{id}/contact-request/reject")
    public ConversationResponse rejectContact(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return exchangeService.respondToContact(id, user, false);
    }

    @PostMapping("/{id}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationReportResponse report(@PathVariable Long id,
            @Valid @RequestBody ConversationReportRequest request, @AuthenticationPrincipal User user) {
        return reportService.report(id, request.reason(), request.details(), user);
    }
}
