package com.margomarket.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "blocked_words")
@Getter
@Setter
@NoArgsConstructor
public class BlockedWord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String term;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_mode", nullable = false, length = 10)
    private MatchMode matchMode = MatchMode.EXACT;

    @Column(nullable = false)
    private boolean enabled = true;

    public enum MatchMode { EXACT, PREFIX }
}
