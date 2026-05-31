package com.rota.tenancy.internal;

import com.rota.common.encryption.EncryptionService;
import com.rota.tenancy.api.TenantProvisioningService;
import com.rota.tenancy.jpa.TenantEntity;
import com.rota.tenancy.jpa.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class TenantProvisioningServiceImpl implements TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final EncryptionService encryptionService;

    TenantProvisioningServiceImpl(TenantRepository tenantRepository, EncryptionService encryptionService) {
        this.tenantRepository = tenantRepository;
        this.encryptionService = encryptionService;
    }

    @Override
    public void createTenant(UUID tenantId, String slug, String name) {
        TenantEntity tenant = new TenantEntity();
        tenant.setId(tenantId);
        tenant.setSlug(slug);
        tenant.setName(name);
        tenant.setPlan("free");
        // Provision the per-tenant DEK inline (wrapped with the KEK) so it lands in the
        // same INSERT — no separate UPDATE / flush-ordering concerns.
        tenant.setEncryptedDek(encryptionService.generateWrappedDek());
        tenantRepository.save(tenant);
    }
}
