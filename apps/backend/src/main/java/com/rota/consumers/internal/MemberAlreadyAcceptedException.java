package com.rota.consumers.internal;

public class MemberAlreadyAcceptedException extends RuntimeException {

    public MemberAlreadyAcceptedException(String email) {
        super(email + " has already accepted membership of this group");
    }
}
