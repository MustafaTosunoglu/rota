/**
 * Cross-cutting infrastructure shared by all other modules: tenant context, the
 * tenant-aware datasource, security configuration, and (later) encryption + audit base.
 *
 * <p>Declared as an {@link org.springframework.modulith.ApplicationModule.Type#OPEN OPEN}
 * module so every other module may depend on its types (e.g. {@code TenantContext})
 * without Spring Modulith flagging a boundary violation.
 */
@ApplicationModule(
        displayName = "Common",
        type = ApplicationModule.Type.OPEN
)
package com.rota.common;

import org.springframework.modulith.ApplicationModule;
