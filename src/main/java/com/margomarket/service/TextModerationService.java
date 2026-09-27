package com.margomarket.service;

import com.margomarket.dto.BlockedWordRequest;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.BlockedWord;
import com.margomarket.repository.BlockedWordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TextModerationService {
    private final BlockedWordRepository repository;
    private static final String WORD = "[\\p{L}\\p{M}\\p{N}_]";

    public List<BlockedWord> list() {
        return repository.findAllByOrderByTermAsc();
    }

    @Transactional
    public BlockedWord save(Long id, BlockedWordRequest request) {
        String term = normalize(request.term().strip().replaceAll("\\s+", " "));
        if (!term.matches("[\\p{L}\\p{N}]+(?: [\\p{L}\\p{N}]+)*")) {
            throw new IllegalArgumentException("Wpisz słowo lub frazę bez znaków specjalnych.");
        }
        String characters = term.replace(" ", "");
        if (characters.codePointCount(0, characters.length()) < 3) {
            throw new IllegalArgumentException("Słowo lub fraza musi mieć co najmniej 3 znaki, nie licząc spacji.");
        }
        if (repository.existsByTermAndIdNot(term, id == null ? -1L : id)) {
            throw new IllegalArgumentException("To słowo lub fraza jest już na liście.");
        }
        BlockedWord word = id == null ? new BlockedWord() : find(id);
        word.setTerm(term);
        word.setMatchMode(request.matchMode());
        word.setEnabled(request.enabled());
        return repository.saveAndFlush(word);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private BlockedWord find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Wpis nie istnieje"));
    }

    public String mask(String text) {
        return masker().apply(text);
    }

    public UnaryOperator<String> masker() {
        List<Pattern> patterns = repository.findByEnabledTrueOrderByTermAsc().stream()
                .map(word -> {
                    String term = String.join("\\s+", java.util.Arrays.stream(normalize(word.getTerm()).split(" "))
                            .map(Pattern::quote).toList());
                    String suffix = word.getMatchMode() == BlockedWord.MatchMode.PREFIX ? WORD + "*" : "";
                    return Pattern.compile("(?<!" + WORD + ")" + term + suffix + "(?!" + WORD + ")");
                }).toList();
        return text -> {
            if (text == null || text.isEmpty() || patterns.isEmpty()) return text;
            String normalized = normalize(text);
            char[] masked = text.toCharArray();
            for (Pattern pattern : patterns) {
                var matcher = pattern.matcher(normalized);
                while (matcher.find()) {
                    for (int i = matcher.start(); i < matcher.end(); i++) {
                        if (!Character.isWhitespace(masked[i])) masked[i] = '*';
                    }
                }
            }
            return new String(masked);
        };
    }

    private static String normalize(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            char lower = Character.toLowerCase(character);
            if (lower == 'ł') lower = 'l';
            String decomposed = Normalizer.normalize(String.valueOf(lower), Normalizer.Form.NFD);
            char base = decomposed.charAt(0);
            result.append(base >= 'a' && base <= 'z' ? base : lower);
        }
        return result.toString();
    }
}
