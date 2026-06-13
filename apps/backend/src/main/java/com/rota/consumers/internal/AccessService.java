package com.rota.consumers.internal;

import com.rota.consumers.jpa.ConsumerGroupRepository;
import com.rota.consumers.jpa.GroupDocumentAccessEntity;
import com.rota.consumers.jpa.GroupDocumentAccessRepository;
import com.rota.consumers.jpa.GroupEndpointAccessEntity;
import com.rota.consumers.jpa.GroupEndpointAccessRepository;
import com.rota.documents.api.DocumentGuard;
import com.rota.endpoints.api.EndpointGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Per-document and per-endpoint (override) access grants for a consumer group. PUT semantics:
 * the grant for (group, target) is replaced as a whole — hence a single upsert per target.
 * Enforcement of these grants happens in the consumer-facing read path (later phase).
 */
@Service
public class AccessService {

    private final ConsumerGroupRepository groups;
    private final GroupDocumentAccessRepository documentAccess;
    private final GroupEndpointAccessRepository endpointAccess;
    private final DocumentGuard documentGuard;
    private final EndpointGuard endpointGuard;

    public AccessService(ConsumerGroupRepository groups,
                         GroupDocumentAccessRepository documentAccess,
                         GroupEndpointAccessRepository endpointAccess,
                         DocumentGuard documentGuard,
                         EndpointGuard endpointGuard) {
        this.groups = groups;
        this.documentAccess = documentAccess;
        this.endpointAccess = endpointAccess;
        this.documentGuard = documentGuard;
        this.endpointGuard = endpointGuard;
    }

    // --- document level --------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<GroupDocumentAccessEntity> listDocumentAccess(UUID groupId) {
        requireGroup(groupId);
        return documentAccess.findAllByGroupId(groupId);
    }

    @Transactional
    public GroupDocumentAccessEntity setDocumentAccess(UUID groupId, UUID documentId,
                                                       boolean canView, boolean canTry, boolean canLoadtest) {
        UUID tenantId = requireGroup(groupId);
        documentGuard.requireDocument(documentId);
        GroupDocumentAccessEntity grant = documentAccess.findByGroupIdAndDocumentId(groupId, documentId)
                .orElseGet(() -> {
                    GroupDocumentAccessEntity created = new GroupDocumentAccessEntity();
                    created.setTenantId(tenantId);
                    created.setGroupId(groupId);
                    created.setDocumentId(documentId);
                    return created;
                });
        grant.setCanView(canView);
        grant.setCanTry(canTry);
        grant.setCanLoadtest(canLoadtest);
        return documentAccess.save(grant);
    }

    @Transactional
    public void removeDocumentAccess(UUID groupId, UUID documentId) {
        requireGroup(groupId);
        GroupDocumentAccessEntity grant = documentAccess.findByGroupIdAndDocumentId(groupId, documentId)
                .orElseThrow(() -> new ConsumerNotFoundException("Document access grant", documentId));
        documentAccess.delete(grant);
    }

    // --- endpoint level (override) -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<GroupEndpointAccessEntity> listEndpointAccess(UUID groupId) {
        requireGroup(groupId);
        return endpointAccess.findAllByGroupId(groupId);
    }

    @Transactional
    public GroupEndpointAccessEntity setEndpointAccess(UUID groupId, UUID endpointId,
                                                       boolean canView, boolean canTry, boolean canLoadtest) {
        UUID tenantId = requireGroup(groupId);
        endpointGuard.requireEndpoint(endpointId);
        GroupEndpointAccessEntity grant = endpointAccess.findByGroupIdAndEndpointId(groupId, endpointId)
                .orElseGet(() -> {
                    GroupEndpointAccessEntity created = new GroupEndpointAccessEntity();
                    created.setTenantId(tenantId);
                    created.setGroupId(groupId);
                    created.setEndpointId(endpointId);
                    return created;
                });
        grant.setCanView(canView);
        grant.setCanTry(canTry);
        grant.setCanLoadtest(canLoadtest);
        return endpointAccess.save(grant);
    }

    @Transactional
    public void removeEndpointAccess(UUID groupId, UUID endpointId) {
        requireGroup(groupId);
        GroupEndpointAccessEntity grant = endpointAccess.findByGroupIdAndEndpointId(groupId, endpointId)
                .orElseThrow(() -> new ConsumerNotFoundException("Endpoint access grant", endpointId));
        endpointAccess.delete(grant);
    }

    private UUID requireGroup(UUID groupId) {
        return groups.findById(groupId)
                .orElseThrow(() -> new ConsumerNotFoundException("Consumer group", groupId))
                .getTenantId();
    }
}
