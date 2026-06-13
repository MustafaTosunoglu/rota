package com.rota.consumers.internal;

import com.rota.common.email.EmailProperties;
import com.rota.common.email.EmailSender;
import com.rota.common.security.CurrentUser;
import com.rota.common.tenant.TenantContext;
import com.rota.consumers.jpa.ConsumerGroupEntity;
import com.rota.consumers.jpa.ConsumerGroupMemberEntity;
import com.rota.consumers.jpa.ConsumerGroupMemberRepository;
import com.rota.consumers.jpa.ConsumerGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Email-based invitation flow (plan 2.6). Invite creates/refreshes a member row with a
 * single-use hashed token and emails the accept link. Accepting requires an authenticated
 * Rota user whose email matches the invite; the acceptor may belong to a DIFFERENT tenant,
 * so accept parses the inviter tenant from the token prefix and binds it BEFORE opening the
 * transaction (context-before-transaction, same pattern as VerificationService).
 */
@Service
public class InvitationService {

    static final Duration INVITE_TTL = Duration.ofDays(7);

    private final ConsumerGroupRepository groups;
    private final ConsumerGroupMemberRepository members;
    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final TransactionTemplate transactionTemplate;

    public InvitationService(ConsumerGroupRepository groups,
                             ConsumerGroupMemberRepository members,
                             EmailSender emailSender,
                             EmailProperties emailProperties,
                             PlatformTransactionManager transactionManager) {
        this.groups = groups;
        this.members = members;
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Invites {@code email} into the group. Re-inviting a pending member rotates the token
     * and re-sends the email; an accepted member cannot be re-invited. The DB write commits
     * first; the email is sent afterwards so a slow mail provider never holds the transaction
     * (same pattern as VerificationService).
     */
    @Transactional
    public ConsumerGroupMemberEntity invite(UUID groupId, String email) {
        ConsumerGroupEntity group = groups.findById(groupId)
                .orElseThrow(() -> new ConsumerNotFoundException("Consumer group", groupId));
        String normalized = email.trim().toLowerCase(Locale.ROOT);

        ConsumerGroupMemberEntity member = members.findByGroupIdAndEmail(groupId, normalized)
                .orElseGet(() -> {
                    ConsumerGroupMemberEntity created = new ConsumerGroupMemberEntity();
                    created.setTenantId(group.getTenantId());
                    created.setGroupId(groupId);
                    created.setEmail(normalized);
                    return created;
                });
        if (ConsumerGroupMemberEntity.STATUS_ACCEPTED.equals(member.getStatus())) {
            throw new MemberAlreadyAcceptedException(normalized);
        }

        String rawToken = InviteTokens.newRawToken(group.getTenantId());
        member.setTokenHash(InviteTokens.hash(rawToken));
        member.setExpiresAt(OffsetDateTime.now().plus(INVITE_TTL));
        member = members.save(member);

        // Send only once the row is durably committed — avoids emailing a link that a later
        // rollback would invalidate, and keeps mail I/O out of the DB transaction.
        String groupName = group.getName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendInviteEmail(normalized, groupName, rawToken);
            }
        });
        return member;
    }

    /**
     * Accepts an invitation on behalf of the CURRENT authenticated user. Token validity is
     * the only gate (it was delivered to the invited mailbox); the invite email and the
     * acceptor's login email may legitimately differ, so they are NOT required to match —
     * review note: tighten here if that policy changes.
     */
    public void accept(String rawToken) {
        UUID inviterTenant = InviteTokens.parseTenantId(rawToken);
        UUID acceptorUserId = CurrentUser.requireId();
        UUID previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(inviterTenant);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                ConsumerGroupMemberEntity member = members
                        .findByTokenHash(InviteTokens.hash(rawToken))
                        .orElseThrow(InvalidInvitationTokenException::new);
                OffsetDateTime now = OffsetDateTime.now();
                if (!ConsumerGroupMemberEntity.STATUS_INVITED.equals(member.getStatus())
                        || member.getExpiresAt().isBefore(now)) {
                    throw new InvalidInvitationTokenException();
                }
                member.setStatus(ConsumerGroupMemberEntity.STATUS_ACCEPTED);
                member.setUserId(acceptorUserId);
                member.setAcceptedAt(now);
                member.setTokenHash(null); // single-use
            });
        } finally {
            // Restore the acceptor's own tenant for the rest of the request.
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void sendInviteEmail(String to, String groupName, String rawToken) {
        String link = emailProperties.getAppBaseUrl() + "/invitation/accept?token=" + rawToken;
        String safeGroup = HtmlUtils.htmlEscape(groupName);
        String html = """
                <p>You have been invited to the consumer group <strong>%s</strong> \
                to access API documentation on Rota.</p>
                <p><a href="%s">Accept the invitation</a></p>
                <p>This link is valid for %d days and can be used once. \
                If you were not expecting it, you can ignore this email.</p>
                """.formatted(safeGroup, link, INVITE_TTL.toDays());
        emailSender.send(to, "You've been invited to API docs on Rota", html);
    }
}
