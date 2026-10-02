package com.margomarket.controller;

import com.margomarket.dto.ChatMessageRequest;
import com.margomarket.dto.ChatMessageResponse;
import com.margomarket.dto.ConversationResponse;
import com.margomarket.dto.PageResponse;
import com.margomarket.dto.StartConversationRequest;
import com.margomarket.mapper.PageMapper;
import com.margomarket.model.User;
import com.margomarket.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService service;
    private final PageMapper pageMapper;

    @GetMapping
    public List<ConversationResponse> list(@AuthenticationPrincipal User user) {
        return service.list(user);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", service.unreadCount(user));
    }

    @GetMapping("/intermediary-requests")
    public List<ConversationResponse> intermediaryRequests(@AuthenticationPrincipal User user) {
        return service.intermediaryRequests(user);
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
        return service.requestIntermediary(id, user);
    }

    @PostMapping("/{id}/join-as-intermediary")
    public ConversationResponse joinAsIntermediary(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.joinAsIntermediary(id, user);
    }
}
