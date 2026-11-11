package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.service.GuildConfigService;

import java.util.List;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

final class AdminAccess {
    private AdminAccess() {
    }

    static boolean isAdmin(GuildConfigService guildConfigService, Member member) {
        List<Long> roleIds = member.getRoles().stream().map(Role::getIdLong).toList();
        return guildConfigService.isAdmin(member.getGuild().getIdLong(), roleIds,
                member.hasPermission(Permission.MANAGE_SERVER));
    }
}
