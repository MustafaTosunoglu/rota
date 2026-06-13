package com.rota.consumers.web;

import com.rota.consumers.jpa.ConsumerGroupEntity;
import com.rota.consumers.jpa.ConsumerGroupMemberEntity;
import com.rota.consumers.jpa.GroupDocumentAccessEntity;
import com.rota.consumers.jpa.GroupEndpointAccessEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Request/response records of the consumers module's REST API. */
public final class ConsumerDtos {

    private ConsumerDtos() {
    }

    public record CreateGroupRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description) {
    }

    public record UpdateGroupRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String description) {
    }

    public record GroupResponse(UUID id, String name, String description,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {

        public static GroupResponse from(ConsumerGroupEntity entity) {
            return new GroupResponse(entity.getId(), entity.getName(), entity.getDescription(),
                    entity.getCreatedAt(), entity.getUpdatedAt());
        }
    }

    public record InviteMemberRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record MemberResponse(UUID id, UUID groupId, String email, String status, UUID userId,
                                 OffsetDateTime invitedAt, OffsetDateTime expiresAt,
                                 OffsetDateTime acceptedAt) {

        public static MemberResponse from(ConsumerGroupMemberEntity entity) {
            return new MemberResponse(entity.getId(), entity.getGroupId(), entity.getEmail(),
                    entity.getStatus(), entity.getUserId(), entity.getInvitedAt(),
                    entity.getExpiresAt(), entity.getAcceptedAt());
        }
    }

    public record AcceptInvitationRequest(@NotBlank String token) {
    }

    /** PUT semantics: the whole grant is replaced, so all three flags are required. */
    public record SetAccessRequest(
            @NotNull Boolean canView,
            @NotNull Boolean canTry,
            @NotNull Boolean canLoadtest) {
    }

    public record DocumentAccessResponse(UUID groupId, UUID documentId,
                                         boolean canView, boolean canTry, boolean canLoadtest) {

        public static DocumentAccessResponse from(GroupDocumentAccessEntity entity) {
            return new DocumentAccessResponse(entity.getGroupId(), entity.getDocumentId(),
                    entity.isCanView(), entity.isCanTry(), entity.isCanLoadtest());
        }
    }

    public record EndpointAccessResponse(UUID groupId, UUID endpointId,
                                         boolean canView, boolean canTry, boolean canLoadtest) {

        public static EndpointAccessResponse from(GroupEndpointAccessEntity entity) {
            return new EndpointAccessResponse(entity.getGroupId(), entity.getEndpointId(),
                    entity.isCanView(), entity.isCanTry(), entity.isCanLoadtest());
        }
    }
}
