package com.margomarket.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class AuctionRulesTest {
    @Test void minimumIsOnePercentRoundedUpWithCurrencyFloor() {
        assertThat(AuctionRules.next("PLN",1)).isEqualTo(101);
        assertThat(AuctionRules.next("PLN",10001)).isEqualTo(10102);
        assertThat(AuctionRules.next("GOLD",1)).isEqualTo(1000001);
        assertThat(AuctionRules.next("GOLD",1500000000L)).isEqualTo(1515000000L);
    }
    @Test void limitsNeverOverflowOrMixCurrencies() {
        assertThat(AuctionRules.next("GOLD",9000000000000000L)).isNull();
        assertThat(AuctionRules.next("PLN",100000000)).isNull();
        assertThatThrownBy(()->AuctionRules.amount("PLN",100000001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->AuctionRules.amount("GOLD",0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->AuctionRules.amount("EUR",1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void durationAndRepeatedExtensionsUseExactInstants() {
        var now=Instant.parse("2026-11-13T12:00:00Z");
        AuctionRules.duration(now,now.plusSeconds(3600));
        AuctionRules.duration(now,now.plusSeconds(7*86400));
        assertThatThrownBy(()->AuctionRules.duration(now,now.plusSeconds(3599))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->AuctionRules.duration(now,now.plusSeconds(7*86400+1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(AuctionRules.extendedEnd(now,now.plusSeconds(60))).isEqualTo(now.plusSeconds(120));
        assertThat(AuctionRules.extendedEnd(now.plusSeconds(60),now.plusSeconds(120))).isEqualTo(now.plusSeconds(180));
    }
}
