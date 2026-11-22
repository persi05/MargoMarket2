/**
 * Rozmowy kupujących ze sprzedającymi, pośrednicy, potwierdzenia wymian i realizacja aukcji.
 */
@ApplicationModule(displayName = "Rozmowy", allowedDependencies = {"shared", "user", "security", "moderation", "textmoderation", "listing", "listing :: auction"})
package com.margomarket.conversation;

import org.springframework.modulith.ApplicationModule;
