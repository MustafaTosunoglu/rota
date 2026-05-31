package com.rota;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the Spring Modulith structure (plan §10, task 1.3): module boundaries are
 * respected and there are no illegal cross-module references. Fails the build if, e.g.,
 * the {@code iam} module ever reaches into another module's internals.
 */
class ModularityTests {

    @Test
    void verifiesModuleStructure() {
        ApplicationModules.of(BackendApplication.class).verify();
    }
}
