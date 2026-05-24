package com.margomarket.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListingFilterTest {

    @Test
    void returnsDefaultLevelRangeWhenLevelsAreMissingOrNegative() {
        ListingFilter filter = new ListingFilter();
        filter.setMinLevel(-5);
        filter.setMaxLevel(null);

        assertThat(filter.getMinLevelOrDefault()).isZero();
        assertThat(filter.getMaxLevelOrDefault()).isEqualTo(300);
    }

    @Test
    void capsLevelRangeAtMaximumSupportedLevel() {
        ListingFilter filter = new ListingFilter();
        filter.setMinLevel(350);
        filter.setMaxLevel(999);

        assertThat(filter.getMinLevelOrDefault()).isEqualTo(300);
        assertThat(filter.getMaxLevelOrDefault()).isEqualTo(300);
    }

    @Test
    void trimsSearchTextAndDetectsBlankSearch() {
        ListingFilter filter = new ListingFilter();
        filter.setSearch("  miecz  ");

        assertThat(filter.hasSearch()).isTrue();
        assertThat(filter.getSearchOrEmpty()).isEqualTo("miecz");

        filter.setSearch("   ");

        assertThat(filter.hasSearch()).isFalse();
        assertThat(filter.getSearchOrEmpty()).isEmpty();
    }
}
