package com.rota.consumers.web;

import com.rota.consumers.internal.AccessService;
import com.rota.consumers.internal.ConsumerGroupService;
import com.rota.consumers.internal.InvitationService;
import com.rota.consumers.web.ConsumerDtos.CreateGroupRequest;
import com.rota.consumers.web.ConsumerDtos.DocumentAccessResponse;
import com.rota.consumers.web.ConsumerDtos.EndpointAccessResponse;
import com.rota.consumers.web.ConsumerDtos.GroupResponse;
import com.rota.consumers.web.ConsumerDtos.InviteMemberRequest;
import com.rota.consumers.web.ConsumerDtos.MemberResponse;
import com.rota.consumers.web.ConsumerDtos.SetAccessRequest;
import com.rota.consumers.web.ConsumerDtos.UpdateGroupRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Consumer group management. Floors are STRICTER than content (member emails + access
 * policy are sensitive): reads need editor, every mutation needs admin.
 */
@RestController
@RequestMapping("/api/v1/consumer-groups")
public class ConsumerGroupController {

    private final ConsumerGroupService groupService;
    private final InvitationService invitationService;
    private final AccessService accessService;

    public ConsumerGroupController(ConsumerGroupService groupService,
                                   InvitationService invitationService,
                                   AccessService accessService) {
        this.groupService = groupService;
        this.invitationService = invitationService;
        this.accessService = accessService;
    }

    // --- groups ---------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasRole('editor')")
    public List<GroupResponse> listConsumerGroups() {
        return groupService.list().stream().map(GroupResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createConsumerGroup(@Valid @RequestBody CreateGroupRequest request) {
        return GroupResponse.from(groupService.create(request.name(), request.description()));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("hasRole('editor')")
    public GroupResponse getConsumerGroup(@PathVariable UUID groupId) {
        return GroupResponse.from(groupService.get(groupId));
    }

    @PatchMapping("/{groupId}")
    @PreAuthorize("hasRole('admin')")
    public GroupResponse updateConsumerGroup(@PathVariable UUID groupId,
                                             @Valid @RequestBody UpdateGroupRequest request) {
        return GroupResponse.from(groupService.update(groupId, request.name(), request.description()));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasRole('admin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConsumerGroup(@PathVariable UUID groupId) {
        groupService.delete(groupId);
    }

    // --- members ---------------------------------------------------------------------

    @GetMapping("/{groupId}/members")
    @PreAuthorize("hasRole('editor')")
    public List<MemberResponse> listGroupMembers(@PathVariable UUID groupId) {
        return groupService.listMembers(groupId).stream().map(MemberResponse::from).toList();
    }

    @PostMapping("/{groupId}/members")
    @PreAuthorize("hasRole('admin')")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse inviteGroupMember(@PathVariable UUID groupId,
                                            @Valid @RequestBody InviteMemberRequest request) {
        return MemberResponse.from(invitationService.invite(groupId, request.email()));
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    @PreAuthorize("hasRole('admin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeGroupMember(@PathVariable UUID groupId, @PathVariable UUID memberId) {
        groupService.removeMember(groupId, memberId);
    }

    // --- document access ---------------------------------------------------------------

    @GetMapping("/{groupId}/document-access")
    @PreAuthorize("hasRole('editor')")
    public List<DocumentAccessResponse> listGroupDocumentAccess(@PathVariable UUID groupId) {
        return accessService.listDocumentAccess(groupId).stream()
                .map(DocumentAccessResponse::from).toList();
    }

    @PutMapping("/{groupId}/document-access/{documentId}")
    @PreAuthorize("hasRole('admin')")
    public DocumentAccessResponse setGroupDocumentAccess(@PathVariable UUID groupId,
                                                         @PathVariable UUID documentId,
                                                         @Valid @RequestBody SetAccessRequest request) {
        return DocumentAccessResponse.from(accessService.setDocumentAccess(groupId, documentId,
                request.canView(), request.canTry(), request.canLoadtest()));
    }

    @DeleteMapping("/{groupId}/document-access/{documentId}")
    @PreAuthorize("hasRole('admin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeGroupDocumentAccess(@PathVariable UUID groupId, @PathVariable UUID documentId) {
        accessService.removeDocumentAccess(groupId, documentId);
    }

    // --- endpoint access (override) ------------------------------------------------------

    @GetMapping("/{groupId}/endpoint-access")
    @PreAuthorize("hasRole('editor')")
    public List<EndpointAccessResponse> listGroupEndpointAccess(@PathVariable UUID groupId) {
        return accessService.listEndpointAccess(groupId).stream()
                .map(EndpointAccessResponse::from).toList();
    }

    @PutMapping("/{groupId}/endpoint-access/{endpointId}")
    @PreAuthorize("hasRole('admin')")
    public EndpointAccessResponse setGroupEndpointAccess(@PathVariable UUID groupId,
                                                         @PathVariable UUID endpointId,
                                                         @Valid @RequestBody SetAccessRequest request) {
        return EndpointAccessResponse.from(accessService.setEndpointAccess(groupId, endpointId,
                request.canView(), request.canTry(), request.canLoadtest()));
    }

    @DeleteMapping("/{groupId}/endpoint-access/{endpointId}")
    @PreAuthorize("hasRole('admin')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeGroupEndpointAccess(@PathVariable UUID groupId, @PathVariable UUID endpointId) {
        accessService.removeEndpointAccess(groupId, endpointId);
    }
}
