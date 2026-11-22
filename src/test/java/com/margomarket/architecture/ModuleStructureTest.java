package com.margomarket.architecture;

import com.margomarket.MargomarketApplication;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the module boundaries. The top package of a module is its public API; everything
 * else sits in sub-packages (usually {@code internal}) that other modules cannot use.
 * The documentation step writes PlantUML diagrams and module canvases to target/spring-modulith-docs.
 */
class ModuleStructureTest {
    static final ApplicationModules MODULES = ApplicationModules.of(MargomarketApplication.class);
    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.margomarket");
    /** The top package of a module, e.g. com.margomarket.listing but not com.margomarket.listing.internal. */
    static final String MODULE_API = "com.margomarket.(*)";

    /**
     * Entities that other modules map JPA relations to. They stay in the module API until those
     * relations are replaced by plain ids; every other entity is internal.
     */
    static final Set<String> SHARED_ENTITIES = Set.of(
            "User", "Listing", "ListingStatus", "Item", "ItemType", "Rarity", "Server", "Currency");

    /** No cycles, no use of another module's internals, only declared dependencies. */
    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }

    @Test
    void repositoriesControllersAndExceptionHandlersAreInternal() {
        DescribedPredicate<JavaClass> outsideSharedKernel = DescribedPredicate.describe(
                "outside the shared kernel", c -> !c.getPackageName().equals("com.margomarket.shared"));
        noClasses()
                .that(DescribedPredicate.<JavaClass>describe("repositories, controllers or exception handlers",
                        c -> c.isAssignableTo(Repository.class)
                                || c.isAnnotatedWith(RestController.class)
                                || c.isAnnotatedWith(RestControllerAdvice.class)).and(outsideSharedKernel))
                .should().resideInAPackage(MODULE_API)
                .because("the top package of a module is its public API")
                .check(CLASSES);
    }

    @Test
    void onlyEntitiesReferencedByOtherModulesArePublic() {
        noClasses()
                .that().areAnnotatedWith(Entity.class)
                .and(DescribedPredicate.describe("not shared between modules", c -> !SHARED_ENTITIES.contains(c.getSimpleName())))
                .should().resideInAPackage(MODULE_API)
                .because("an entity other modules do not map to belongs to the module internals")
                .check(CLASSES);
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
