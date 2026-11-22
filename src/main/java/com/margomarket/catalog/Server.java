package com.margomarket.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "servers")
@Getter
@Setter
@NoArgsConstructor
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "world_type", nullable = false, length = 16)
    private WorldType worldType = WorldType.TRANSFERABLE;

    /** Gold units bought for 1 PLN. */
    @Column(name = "gold_per_pln")
    private Long goldPerPln;

    /** Price of 1 000 000 upgrade points in grosze. */
    @Column(name = "upgrade_million_price_grosze")
    private Long upgradeMillionPriceGrosze;

    @Column(name = "essence_price_grosze")
    private Long essencePriceGrosze;

    @Column(name = "rates_updated_at")
    private Instant ratesUpdatedAt;
}
