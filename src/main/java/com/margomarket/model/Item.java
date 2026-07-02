package com.margomarket.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private Long externalId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "icon_url", nullable = false, columnDefinition = "TEXT")
    private String iconUrl;

    @Column(nullable = false)
    private Integer level;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_type_id", nullable = false)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rarity_id", nullable = false)
    private Rarity rarity;

    @Column(nullable = false, length = 50)
    private String source = "margolab";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String stats;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "market_enabled", nullable = false)
    private boolean marketEnabled = true;
}
