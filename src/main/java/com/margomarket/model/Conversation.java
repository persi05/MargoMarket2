package com.margomarket.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id")
    private Listing listing;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intermediary_id")
    private User intermediary;

    @Column(name = "intermediary_status", nullable = false, length = 20)
    private String intermediaryStatus = "NONE";

    @Column(name = "buyer_last_read_id", nullable = false)
    private Long buyerLastReadId = 0L;

    @Column(name = "seller_last_read_id", nullable = false)
    private Long sellerLastReadId = 0L;

    @Column(name = "intermediary_last_read_id", nullable = false)
    private Long intermediaryLastReadId = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }
}
