package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.MemberSnapshot;

import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public final class MemberSnapshots {
    private MemberSnapshots() {
    }

    public static MemberSnapshot of(Member member) {
        return new MemberSnapshot(
                member.getGuild().getIdLong(),
                member.getIdLong(),
                member.getUser().getTimeCreated().toInstant(),
                member.getTimeJoined().toInstant(),
                member.getRoles().stream().map(Role::getIdLong).collect(Collectors.toSet()),
                member.getUser().getAvatarId() != null);
    }
}
