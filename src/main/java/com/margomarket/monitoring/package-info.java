/**
 * Health checks and business metrics for Actuator. Reads the database directly and depends on no other module.
 */
@ApplicationModule(displayName = "Monitoring", allowedDependencies = {})
package com.margomarket.monitoring;

import org.springframework.modulith.ApplicationModule;
