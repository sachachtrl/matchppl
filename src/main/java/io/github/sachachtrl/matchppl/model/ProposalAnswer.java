package io.github.sachachtrl.matchppl.model;

public enum ProposalAnswer {
    PENDING,
    ACCEPTED,
    SKIPPED,
    CANCELLED;

    public boolean keepsMemberBusy() {
        return this == PENDING || this == ACCEPTED;
    }
}
