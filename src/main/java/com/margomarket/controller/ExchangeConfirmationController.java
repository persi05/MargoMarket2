package com.margomarket.controller;
import com.margomarket.service.ExchangeConfirmationService;
import com.margomarket.dto.ConversationResponse;
import com.margomarket.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
public class ExchangeConfirmationController {
 private final ExchangeConfirmationService service;
 @GetMapping("/api/admin/exchange-confirmations")
 public com.margomarket.dto.PageResponse<java.util.Map<String,Object>> page(@AuthenticationPrincipal User user,@RequestParam(defaultValue="1") int page){return service.page(user,page);}
 public record RevokeRequest(String reason) {}
 @PostMapping("/api/conversations/{id}/exchange-confirmation")
 public ConversationResponse confirm(@PathVariable Long id,@AuthenticationPrincipal User user) { return service.confirm(id,user); }
 @PostMapping("/api/admin/exchange-confirmations/{id}/revoke")
 public void revoke(@PathVariable Long id,@AuthenticationPrincipal User user,@RequestBody RevokeRequest request) { service.revoke(id,user,request.reason()); }
}
