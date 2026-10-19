package com.margomarket.controller;

import com.margomarket.dto.BlockedWordRequest;
import com.margomarket.model.BlockedWord;
import com.margomarket.dto.PageResponse;
import com.margomarket.mapper.PageMapper;
import com.margomarket.service.TextModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/blocked-words")
@RequiredArgsConstructor
public class ModerationController {
    private final TextModerationService moderation;
    private final PageMapper pageMapper;

    @GetMapping
    public PageResponse<BlockedWord> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "") String search) {
        return pageMapper.toResponse(moderation.listPage(search, page), word -> word);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockedWord create(@Valid @RequestBody BlockedWordRequest request) {
        return moderation.save(null, request);
    }

    @PutMapping("/{id}")
    public BlockedWord update(@PathVariable Long id, @Valid @RequestBody BlockedWordRequest request) {
        return moderation.save(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { moderation.delete(id); }
}
