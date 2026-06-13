package com.rota.consumers.web;

import com.rota.consumers.internal.InvitationService;
import com.rota.consumers.web.ConsumerDtos.AcceptInvitationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitation acceptance. Authenticated (any tenant — external consumers accept with their
 * own Rota account); the inviter's tenant scope comes from the token itself. Unregistered
 * invitees sign up first, then accept (frontend wires this in a later phase).
 */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        invitationService.accept(request.token());
    }
}
