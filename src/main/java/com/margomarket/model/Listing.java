package com.margomarket.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id")
    private Item item;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_type_id", nullable = false)
    private ItemType itemType;

    @Column(nullable = false)
    private Integer level;

    @Column(name = "enhancement_level", nullable = false)
    private Integer enhancementLevel = 0;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rarity_id", nullable = false)
    private Rarity rarity;

    @Column(nullable = false)
    private Integer price;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(nullable = false, length = 255)
    private String contact;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id", nullable = false)
    private ListingStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return "active".equals(status.getName());
    }

    public boolean isSold() {
        return "sold".equals(status.getName());
    }

    public String getFormattedPrice() {
        NumberFormat fmt = NumberFormat.getIntegerInstance(new Locale("pl", "PL"));
        String formatted = fmt.format(price).replace('\u00a0', ' ');
        if ("w grze".equals(currency.getName())) {
            return formatted + " złota";
        }
        return formatted + " PLN";
    }
}
