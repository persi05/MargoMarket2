package com.margomarket.architecture;

import com.margomarket.MargomarketApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Guards the module boundaries: no dependency cycles between modules and no module
 * reaching into another module's internal packages. The documentation step writes
 * PlantUML diagrams and module canvases to target/spring-modulith-docs.
 */
class ModuleStructureTest {
    static final ApplicationModules MODULES = ApplicationModules.of(MargomarketApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
