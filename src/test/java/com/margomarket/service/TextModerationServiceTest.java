package com.margomarket.service;

import com.margomarket.dto.BlockedWordRequest;
import com.margomarket.model.BlockedWord;
import com.margomarket.model.BlockedWord.MatchMode;
import com.margomarket.repository.BlockedWordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class TextModerationServiceTest {
    @Mock private BlockedWordRepository repository;
    @InjectMocks private TextModerationService service;

    @Test
    void masksPolishAccentsUppercaseAndEnglishWhilePreservingPunctuationAndEmoji() {
        rules(word("zolc", MatchMode.EXACT), word("fuck", MatchMode.PREFIX));
        assertThat(service.mask("🙂 ŻÓŁĆ! Fucking, żółć i normalny tekst."))
                .isEqualTo("🙂 ****! *******, **** i normalny tekst.");
    }

    @Test
    void exactMatchesDoNotCensorSubstringsInOrdinaryWords() {
        rules(word("ass", MatchMode.EXACT), word("dupa", MatchMode.EXACT));
        assertThat(service.mask("class, assassin, dupa, duplikat; ass!"))
                .isEqualTo("class, assassin, ****, duplikat; ***!");
    }

    @Test
    void prefixesMaskWholeInflectedWordsButRequireBeginningOfWord() {
        rules(word("kurw", MatchMode.PREFIX));
        assertThat(service.mask("KURWA kurwami xkurwa"))
                .isEqualTo("***** ******* xkurwa");
    }

    @Test
    void phrasesSupportWhitespaceAndOverlappingRules() {
        rules(word("bad word", MatchMode.EXACT), word("word", MatchMode.EXACT));
        assertThat(service.mask("bad\n WORD bad words"))
                .isEqualTo("***\n **** bad words");
    }

    @Test
    void newSnapshotsUseCurrentRulesAndEmptyDictionaryLeavesTextUnchanged() {
        when(repository.findByEnabledTrueOrderByTermAsc()).thenReturn(List.of(word("test", MatchMode.EXACT)), List.of());
        assertThat(service.mask("test")).isEqualTo("****");
        assertThat(service.mask("test")).isEqualTo("test");
    }

    @Test
    void savesNormalizedTermAndEnabledState() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var word = service.save(null, new BlockedWordRequest("  ŻÓŁĆ   słowo  ", MatchMode.EXACT, false));
        assertThat(word.getTerm()).isEqualTo("zolc slowo");
        assertThat(word.isEnabled()).isFalse();
    }

    @Test
    void rejectsDuplicatesRegexInputAndShortPrefixes() {
        when(repository.existsByTermAndIdNot("test", -1L)).thenReturn(true);
        assertThatThrownBy(() -> service.save(null, new BlockedWordRequest("TEST", MatchMode.EXACT, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("już");
        assertThatThrownBy(() -> service.save(null, new BlockedWordRequest(".*", MatchMode.EXACT, true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(null, new BlockedWordRequest("ab", MatchMode.PREFIX, true)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void canDisableExistingRuleAndDeleteIt() {
        BlockedWord word = word("test", MatchMode.EXACT);
        word.setId(5L);
        when(repository.findById(5L)).thenReturn(Optional.of(word));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.save(5L, new BlockedWordRequest("test", MatchMode.PREFIX, false));
        assertThat(word.isEnabled()).isFalse();
        assertThat(word.getMatchMode()).isEqualTo(MatchMode.PREFIX);
        service.delete(5L);
        verify(repository).delete(word);
    }

    @Test
    void requiresThreeNonSpaceCharactersForBothModes() {
        for (MatchMode mode : MatchMode.values()) {
            for (String term : List.of("a", "ab", " a b ", " a    ")) {
                assertThatThrownBy(() -> service.save(null, new BlockedWordRequest(term, mode, true)))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("3 znaki");
            }
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void acceptsThreeCharactersEvenWhenSeparatedBySpaces() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.save(null, new BlockedWordRequest(" a b c ", MatchMode.EXACT, true)).getTerm())
                .isEqualTo("a b c");
    }

    private void rules(BlockedWord... words) {
        when(repository.findByEnabledTrueOrderByTermAsc()).thenReturn(List.of(words));
    }

    private BlockedWord word(String term, MatchMode mode) {
        BlockedWord word = new BlockedWord();
        word.setTerm(term);
        word.setMatchMode(mode);
        return word;
    }
}
