package io.github.sachachtrl.matchppl.model;

public record UserPair(long firstUserId, long secondUserId) {

    public UserPair {
        if (firstUserId > secondUserId) {
            long swap = firstUserId;
            firstUserId = secondUserId;
            secondUserId = swap;
        }
    }
}
