/**
 * Limity zapytań i prób logowania.
 */
@ApplicationModule(displayName = "Limity zapytań", allowedDependencies = {"shared", "user"})
package com.margomarket.ratelimit;

import org.springframework.modulith.ApplicationModule;
