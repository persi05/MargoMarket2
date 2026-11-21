package com.margomarket.ratelimit;

import io.github.bucket4j.TimeMeter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
    @Bean
    public RateLimiter rateLimiter(RateLimitProperties properties) {
        return new RateLimiter(properties, TimeMeter.SYSTEM_NANOTIME);
    }
}
