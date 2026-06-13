package com.rota.consumers.internal;

import com.rota.common.tenant.TenantContext;
import com.rota.consumers.jpa.ConsumerGroupEntity;
import com.rota.consumers.jpa.ConsumerGroupMemberEntity;
import com.rota.consumers.jpa.ConsumerGroupMemberRepository;
import com.rota.consumers.jpa.ConsumerGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Consumer group CRUD + member listing/removal. Invitations live in {@link InvitationService}. */
@Service
public class ConsumerGroupService {

    private final ConsumerGroupRepository groups;
    private final ConsumerGroupMemberRepository members;

    public ConsumerGroupService(ConsumerGroupRepository groups, ConsumerGroupMemberRepository members) {
        this.groups = groups;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public List<ConsumerGroupEntity> list() {
        return groups.findAllByOrderByName();
    }

    @Transactional(readOnly = true)
    public ConsumerGroupEntity get(UUID groupId) {
        return groups.findById(groupId)
                .orElseThrow(() -> new ConsumerNotFoundException("Consumer group", groupId));
    }

    @Transactional
    public ConsumerGroupEntity create(String name, String description) {
        String trimmed = name.trim();
        if (groups.existsByName(trimmed)) {
            throw new GroupNameAlreadyInUseException(trimmed);
        }
        ConsumerGroupEntity group = new ConsumerGroupEntity();
        group.setTenantId(TenantContext.getTenantId());
        group.setName(trimmed);
        group.setDescription(description);
        return groups.save(group);
    }

    @Transactional
    public ConsumerGroupEntity update(UUID groupId, String name, String description) {
        ConsumerGroupEntity group = get(groupId);
        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            if (!trimmed.equals(group.getName()) && groups.existsByName(trimmed)) {
                throw new GroupNameAlreadyInUseException(trimmed);
            }
            group.setName(trimmed);
        }
        if (description != null) {
            group.setDescription(description);
        }
        return group;
    }

    /** Members and access grants go with the group (FK CASCADE). */
    @Transactional
    public void delete(UUID groupId) {
        groups.delete(get(groupId));
    }

    @Transactional(readOnly = true)
    public List<ConsumerGroupMemberEntity> listMembers(UUID groupId) {
        get(groupId);
        return members.findAllByGroupIdOrderByEmail(groupId);
    }

    @Transactional
    public void removeMember(UUID groupId, UUID memberId) {
        get(groupId);
        ConsumerGroupMemberEntity member = members.findById(memberId)
                .filter(m -> m.getGroupId().equals(groupId))
                .orElseThrow(() -> new ConsumerNotFoundException("Member", memberId));
        members.delete(member);
    }
}
