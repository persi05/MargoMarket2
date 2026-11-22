package com.margomarket.textmoderation;

import com.margomarket.shared.PageMapper;
import com.margomarket.shared.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Administracja: blokowane słowa", description = "Słownik moderacji tekstu")
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
