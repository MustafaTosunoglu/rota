package com.rota.iam.internal;

import com.rota.common.tenant.TenantContext;
import com.rota.iam.api.UserRegisteredEvent;
import com.rota.iam.jpa.RoleEntity;
import com.rota.iam.jpa.RoleRepository;
import com.rota.iam.jpa.UserEntity;
import com.rota.iam.jpa.UserRepository;
import com.rota.iam.jpa.UserRoleEntity;
import com.rota.iam.jpa.UserRoleRepository;
import com.rota.tenancy.api.TenantProvisioningService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates signup: in a single transaction creates the tenant (+ DEK), the four system
 * roles, the owner user (Argon2id-hashed password), and the owner role assignment.
 *
 * <p><b>Context-before-transaction:</b> the tenant id is generated here, so it must be bound
 * to {@link TenantContext} BEFORE the transaction starts (the connection — and its
 * {@code app.current_tenant_id} GUC — is taken when the transaction begins). Hence a
 * programmatic {@link TransactionTemplate} rather than {@code @Transactional} on the method.
 */
@Service
public class RegistrationService {

    /** System roles created for every new tenant; "owner" is assigned to the signup user. */
    private static final List<String> SYSTEM_ROLES = List.of("owner", "admin", "editor", "viewer");
    private static final String OWNER_ROLE = "owner";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_unique";

    private final TenantProvisioningService tenantProvisioning;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;

    public RegistrationService(TenantProvisioningService tenantProvisioning,
                               UserRepository userRepository,
                               RoleRepository roleRepository,
                               UserRoleRepository userRoleRepository,
                               PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher events,
                               PlatformTransactionManager transactionManager) {
        this.tenantProvisioning = tenantProvisioning;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public RegistrationResult register(RegistrationCommand command) {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        try {
            RegistrationResult result = transactionTemplate.execute(status -> doRegister(tenantId, command));
            events.publishEvent(new UserRegisteredEvent(tenantId, result.userId(), command.email()));
            return result;
        } catch (DataIntegrityViolationException ex) {
            if (isEmailConflict(ex)) {
                throw new EmailAlreadyInUseException(command.email());
            }
            throw ex;
        } finally {
            TenantContext.clear();
        }
    }

    private RegistrationResult doRegister(UUID tenantId, RegistrationCommand command) {
        String slug = Slugs.slugify(command.organizationName()) + "-" + Slugs.randomSuffix();
        tenantProvisioning.createTenant(tenantId, slug, command.organizationName());

        RoleEntity ownerRole = null;
        for (String roleName : SYSTEM_ROLES) {
            RoleEntity role = new RoleEntity();
            role.setTenantId(tenantId);
            role.setName(roleName);
            role.setSystem(true);
            role = roleRepository.save(role);
            if (OWNER_ROLE.equals(roleName)) {
                ownerRole = role;
            }
        }

        UserEntity user = new UserEntity();
        user.setTenantId(tenantId);
        user.setEmail(command.email());
        user.setDisplayName(command.displayName());
        user.setLocale(command.locale());
        user.setPasswordHash(passwordEncoder.encode(command.rawPassword()));
        user = userRepository.save(user);

        userRoleRepository.save(new UserRoleEntity(user.getId(), ownerRole.getId(), tenantId));

        return new RegistrationResult(tenantId, user.getId());
    }

    private boolean isEmailConflict(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(EMAIL_UNIQUE_CONSTRAINT);
    }

    /** Normalised signup input. {@code email} is expected already lowercased/trimmed. */
    public record RegistrationCommand(String email,
                                      String rawPassword,
                                      String displayName,
                                      String organizationName,
                                      String locale) {
    }

    public record RegistrationResult(UUID tenantId, UUID userId) {
    }
}
