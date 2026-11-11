package io.github.sachachtrl.matchppl.model;

public record MatchPair(long firstUserId, long secondUserId, int commonTags, int largestTagCount) {

    public double score() {
        return this.largestTagCount == 0 ? 0 : (double) this.commonTags / this.largestTagCount;
    }

    public UserPair users() {
        return new UserPair(this.firstUserId, this.secondUserId);
    }
}
