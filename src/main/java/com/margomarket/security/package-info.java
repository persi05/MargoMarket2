/**
 * JWT, logowanie i reguły dostępu do endpointów.
 */
@ApplicationModule(displayName = "Bezpieczeństwo", allowedDependencies = {"shared", "user", "moderation", "ratelimit"})
package com.margomarket.security;

import org.springframework.modulith.ApplicationModule;
