package com.margomarket.pricing;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Price settings shared by all worlds (currently the dragon scale price used for unbinding costs). */
@Service
@RequiredArgsConstructor
public class PricingSettingsService {
    public record Settings(Long scalesHundredPriceGrosze) {}

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Settings get() {
        return new Settings(jdbc.queryForObject("SELECT scales_hundred_price_grosze FROM pricing_settings WHERE id = 1", Long.class));
    }

    @Transactional
    public Settings update(Long scalesHundredPriceGrosze) {
        jdbc.update("UPDATE pricing_settings SET scales_hundred_price_grosze = ?, updated_at = clock_timestamp() WHERE id = 1",
                scalesHundredPriceGrosze);
        return get();
    }
}
