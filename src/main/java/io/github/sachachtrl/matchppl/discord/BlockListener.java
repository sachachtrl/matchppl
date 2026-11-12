package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.service.BlockService;
import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.ProposalService;

import java.util.Locale;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(BlockListener.class);

    private static final String BLOCK_COMMAND = "block";
    private static final String UNBLOCK_COMMAND = "unblock";
    private static final String BLOCKLIST_COMMAND = "blocklist";
    private static final String USER_OPTION = "user";

    private final BlockService blockService;
    private final GuildConfigService guildConfigService;
    private final ProposalMessenger messenger;

    public BlockListener(BlockService blockService, GuildConfigService guildConfigService,
            ProposalMessenger messenger) {
        this.blockService = blockService;
        this.guildConfigService = guildConfigService;
        this.messenger = messenger;
    }

    public static SlashCommandData blockCommand() {
        return userCommand(BLOCK_COMMAND);
    }

    public static SlashCommandData unblockCommand() {
        return userCommand(UNBLOCK_COMMAND);
    }

    public static SlashCommandData blockListCommand() {
        return Commands.slash(BLOCKLIST_COMMAND,
                        Messages.get(Messages.DEFAULT_LOCALE, "command.blocklist.description"))
                .setContexts(InteractionContextType.GUILD);
    }

    private static SlashCommandData userCommand(String name) {
        Locale locale = Messages.DEFAULT_LOCALE;
        return Commands.slash(name, Messages.get(locale, "command." + name + ".description"))
                .addOption(OptionType.USER, USER_OPTION, Messages.get(locale, "command." + name + ".option.user"), true)
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String name = event.getName();
        Guild guild = event.getGuild();
        if ((!name.equals(BLOCK_COMMAND) && !name.equals(UNBLOCK_COMMAND) && !name.equals(BLOCKLIST_COMMAND))
                || guild == null) {
            return;
        }
        long guildId = guild.getIdLong();
        long userId = event.getUser().getIdLong();
        Locale locale = this.guildConfigService.language(guildId);
        if (name.equals(BLOCKLIST_COMMAND)) {
            this.reply(event, BlockViews.list(this.blockService.blockedMembers(guildId, userId), locale));
            return;
        }
        User target = event.getOption(USER_OPTION, OptionMapping::getAsUser);
        if (target == null) {
            return;
        }
        Container card = name.equals(BLOCK_COMMAND)
                ? this.block(event, guildId, userId, target, locale)
                : this.unblock(guildId, userId, target, locale);
        this.reply(event, card);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        Guild guild = event.getGuild();
        if (!id.startsWith(BlockViews.UNBLOCK_PREFIX) || guild == null) {
            return;
        }
        long guildId = guild.getIdLong();
        long userId = event.getUser().getIdLong();
        long blockedId = Long.parseLong(id.substring(BlockViews.UNBLOCK_PREFIX.length()));
        if (this.blockService.unblock(guildId, userId, blockedId)) {
            log.info("Member unblocked: guild={}", guildId);
        }
        event.editComponents(BlockViews.list(this.blockService.blockedMembers(guildId, userId),
                        this.guildConfigService.language(guildId)))
                .useComponentsV2()
                .queue();
    }

    private Container block(SlashCommandInteractionEvent event, long guildId, long userId, User target,
            Locale locale) {
        if (target.isBot()) {
            return Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.invalid_title"),
                    Messages.get(locale, "block.error.bot"));
        }
        BlockService.BlockResult result = this.blockService.block(guildId, userId, target.getIdLong());
        return switch (result.outcome()) {
            case SELF -> Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.invalid_title"),
                    Messages.get(locale, "block.error.self"));
            case ALREADY_BLOCKED -> Cards.simple(Cards.NEUTRAL_COLOR, Messages.get(locale, "block.already.title"),
                    Messages.format(locale, "block.already.text", target.getAsMention()));
            case BLOCKED -> {
                log.info("Member blocked: guild={}", guildId);
                String withdrawalKey = null;
                if (result.withdrawal().orElse(null) instanceof ProposalService.Skipped skipped) {
                    this.showWithdrawal(event, skipped.proposal(), userId, ProposalViews.CardState.SKIPPED);
                    withdrawalKey = "block.done.skipped";
                } else if (result.withdrawal().orElse(null) instanceof ProposalService.Cancelled cancelled) {
                    this.showWithdrawal(event, cancelled.proposal(), userId, ProposalViews.CardState.CANCELLED);
                    withdrawalKey = "block.done.cancelled";
                }
                yield BlockViews.blocked(target.getAsMention(), withdrawalKey, locale);
            }
        };
    }

    private Container unblock(long guildId, long userId, User target, Locale locale) {
        if (this.blockService.unblock(guildId, userId, target.getIdLong())) {
            log.info("Member unblocked: guild={}", guildId);
            return Cards.simple(Cards.SUCCESS_COLOR, Messages.get(locale, "block.unblocked.title"),
                    Messages.format(locale, "block.unblocked.text", target.getAsMention()));
        }
        return Cards.simple(Cards.NEUTRAL_COLOR, Messages.get(locale, "block.not_blocked.title"),
                Messages.format(locale, "block.not_blocked.text", target.getAsMention()));
    }

    private void showWithdrawal(SlashCommandInteractionEvent event, Proposal proposal, long userId,
            ProposalViews.CardState state) {
        this.messenger.update(event.getJDA(), proposal, userId, state);
    }

    private void reply(SlashCommandInteractionEvent event, Container card) {
        event.replyComponents(card).useComponentsV2().setEphemeral(true).queue();
    }
}
