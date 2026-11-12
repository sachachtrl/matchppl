package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.ProposalService;

import java.util.Locale;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResetListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ResetListener.class);

    private static final String RESET_COMMAND = "reset";
    private static final String RESET_ALL_COMMAND = "resetall";
    private static final String USER_OPTION = "user";
    private static final String PREFIX = "reset:";
    private static final String CONFIRM_USER_PREFIX = PREFIX + "user:";
    private static final String CONFIRM_ALL = PREFIX + "all";
    private static final String CANCEL = PREFIX + "cancel";

    private final ProposalService proposalService;
    private final GuildConfigService guildConfigService;

    public ResetListener(ProposalService proposalService, GuildConfigService guildConfigService) {
        this.proposalService = proposalService;
        this.guildConfigService = guildConfigService;
    }

    public static SlashCommandData resetCommand() {
        Locale locale = Messages.DEFAULT_LOCALE;
        return Commands.slash(RESET_COMMAND, Messages.get(locale, "command.reset.description"))
                .addOption(OptionType.USER, USER_OPTION, Messages.get(locale, "command.reset.option.user"), true)
                .setContexts(InteractionContextType.GUILD);
    }

    public static SlashCommandData resetAllCommand() {
        return Commands.slash(RESET_ALL_COMMAND, Messages.get(Messages.DEFAULT_LOCALE, "command.resetall.description"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if ((!commandName.equals(RESET_COMMAND) && !commandName.equals(RESET_ALL_COMMAND))
                || guild == null || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        if (!this.checkAdmin(event, member, locale)) {
            return;
        }
        Container card;
        if (commandName.equals(RESET_COMMAND)) {
            User target = event.getOption(USER_OPTION, OptionMapping::getAsUser);
            if (target == null) {
                return;
            }
            card = this.confirmUserCard(guild.getIdLong(), target, locale);
        } else {
            card = this.confirmAllCard(guild.getIdLong(), locale);
        }
        event.replyComponents(card).useComponentsV2().setEphemeral(true).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!id.startsWith(PREFIX) || guild == null || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        if (!this.checkAdmin(event, member, locale)) {
            return;
        }
        long guildId = guild.getIdLong();
        Container card;
        if (id.equals(CANCEL)) {
            card = Cards.simple(Cards.MUTED_COLOR, Messages.get(locale, "reset.cancelled.title"),
                    Messages.get(locale, "reset.cancelled.text"));
        } else if (id.equals(CONFIRM_ALL)) {
            int count = this.proposalService.resetAllBlocks(guildId);
            log.info("Match history reset: guild={}, scope=all, pairs={}, by={}", guildId, count, member.getIdLong());
            card = Cards.simple(Cards.SUCCESS_COLOR, Messages.get(locale, "reset.done.title"),
                    count == 1
                            ? Messages.get(locale, "reset.done.all.one")
                            : Messages.format(locale, "reset.done.all.other", count));
        } else if (id.startsWith(CONFIRM_USER_PREFIX)) {
            long userId = Long.parseLong(id.substring(CONFIRM_USER_PREFIX.length()));
            int count = this.proposalService.resetBlocks(guildId, userId);
            log.info("Match history reset: guild={}, user={}, pairs={}, by={}",
                    guildId, userId, count, member.getIdLong());
            String mention = "<@" + userId + ">";
            card = Cards.simple(Cards.SUCCESS_COLOR, Messages.get(locale, "reset.done.title"),
                    count == 1
                            ? Messages.format(locale, "reset.done.user.one", mention)
                            : Messages.format(locale, "reset.done.user.other", count, mention));
        } else {
            return;
        }
        event.editComponents(card).useComponentsV2().queue();
    }

    private Container confirmUserCard(long guildId, User target, Locale locale) {
        String mention = target.getAsMention();
        int count = this.proposalService.countBlockedPairs(guildId, target.getIdLong());
        if (count == 0) {
            return Cards.simple(Cards.NEUTRAL_COLOR, Messages.get(locale, "reset.nothing.title"),
                    Messages.format(locale, "reset.user.nothing", mention));
        }
        String countText = count == 1
                ? Messages.get(locale, "reset.user.count.one")
                : Messages.format(locale, "reset.user.count.other", count);
        return confirmationCard(Cards.WARNING_COLOR,
                Messages.format(locale, "reset.user.title", target.getEffectiveName()),
                Messages.format(locale, "reset.user.explanation", mention), countText,
                Button.danger(CONFIRM_USER_PREFIX + target.getIdLong(), Messages.get(locale, "reset.user.confirm")),
                locale);
    }

    private Container confirmAllCard(long guildId, Locale locale) {
        int count = this.proposalService.countBlockedPairs(guildId);
        if (count == 0) {
            return Cards.simple(Cards.NEUTRAL_COLOR, Messages.get(locale, "reset.nothing.title"),
                    Messages.get(locale, "reset.all.nothing"));
        }
        String countText = count == 1
                ? Messages.get(locale, "reset.all.count.one")
                : Messages.format(locale, "reset.all.count.other", count);
        return confirmationCard(Cards.ERROR_COLOR, Messages.get(locale, "reset.all.title"),
                Messages.get(locale, "reset.all.explanation"), countText,
                Button.danger(CONFIRM_ALL, Messages.get(locale, "reset.all.confirm")), locale);
    }

    private static Container confirmationCard(int color, String title, String explanation, String countText,
            Button confirm, Locale locale) {
        return Container.of(
                TextDisplay.of("## " + title),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(explanation),
                TextDisplay.of(countText),
                TextDisplay.of(Messages.get(locale, "reset.irreversible")),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(confirm, Button.secondary(CANCEL, Messages.get(locale, "reset.cancel")))
        ).withAccentColor(color);
    }

    private boolean checkAdmin(IReplyCallback event, Member member, Locale locale) {
        if (AdminAccess.isAdmin(this.guildConfigService, member)) {
            return true;
        }
        event.replyComponents(Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.title"),
                        Messages.get(locale, "error.admin_required")))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
        return false;
    }
}
