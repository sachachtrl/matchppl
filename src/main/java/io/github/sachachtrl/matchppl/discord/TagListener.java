package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.TagService;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TagListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(TagListener.class);

    private static final String NEWTAG_COMMAND = "newtag";
    private static final String TAGS_COMMAND = "tags";
    private static final String NAME_OPTION = "name";
    private static final String EMOJI_OPTION = "emoji";
    private static final String MODAL_PREFIX = "tagmodal:";
    private static final String VALUE_INPUT = "value";
    private static final String MESSAGES_INPUT = "messages";
    private static final String PERIOD_INPUT = "period";

    private record ComponentTarget(String action, long tagId) {}

    private final TagService tagService;
    private final GuildConfigService guildConfigService;

    public TagListener(TagService tagService, GuildConfigService guildConfigService) {
        this.tagService = tagService;
        this.guildConfigService = guildConfigService;
    }

    public static SlashCommandData newTagCommand() {
        Locale locale = Messages.DEFAULT_LOCALE;
        return Commands.slash(NEWTAG_COMMAND, Messages.get(locale, "command.newtag.description"))
                .addOption(OptionType.STRING, NAME_OPTION, Messages.get(locale, "command.newtag.option.name"), true)
                .addOption(OptionType.STRING, EMOJI_OPTION, Messages.get(locale, "command.newtag.option.emoji"), true)
                .setContexts(InteractionContextType.GUILD);
    }

    public static SlashCommandData tagsCommand() {
        return Commands.slash(TAGS_COMMAND, Messages.get(Messages.DEFAULT_LOCALE, "command.tags.description"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if ((!commandName.equals(NEWTAG_COMMAND) && !commandName.equals(TAGS_COMMAND))
                || guild == null || member == null) {
            return;
        }
        Locale locale = this.guildConfigService.language(guild.getIdLong());
        if (!this.isAdmin(member)) {
            this.replyError(event, Messages.get(locale, "error.title"), Messages.get(locale, "error.admin_required"));
            return;
        }
        if (commandName.equals(TAGS_COMMAND)) {
            event.replyComponents(TagViews.listCard(this.tagService.listTags(guild.getIdLong()), 0, locale))
                    .useComponentsV2()
                    .setEphemeral(true)
                    .queue();
            return;
        }
        String name = event.getOption(NAME_OPTION, OptionMapping::getAsString);
        String emojiInput = event.getOption(EMOJI_OPTION, OptionMapping::getAsString);
        if (name == null || emojiInput == null) {
            return;
        }
        EmojiUnion emoji = Emoji.fromFormatted(emojiInput.strip());
        if (emoji.getType() == Emoji.Type.CUSTOM) {
            guild.retrieveEmojiById(emoji.asCustom().getIdLong()).queue(
                    found -> this.createTag(event, guild, member, name, found.getFormatted(), locale),
                    error -> this.replyInvalidEmoji(event, emojiInput, locale));
        } else if (TagEmojis.isValidUnicode(emojiInput.strip())) {
            this.createTag(event, guild, member, name, emojiInput.strip(), locale);
        } else {
            this.replyInvalidEmoji(event, emojiInput, locale);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        ComponentTarget target = parseTarget(event.getComponentId(), TagViews.COMPONENT_PREFIX);
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (target == null || guild == null || member == null) {
            return;
        }
        Locale locale = this.guildConfigService.language(guild.getIdLong());
        if (!this.isAdmin(member)) {
            this.replyError(event, Messages.get(locale, "error.title"), Messages.get(locale, "error.admin_required"));
            return;
        }
        long guildId = guild.getIdLong();
        if (target.action().equals(TagViews.LIST)) {
            int page = (int) target.tagId();
            event.editComponents(TagViews.listCard(this.tagService.listTags(guildId), page, locale))
                    .useComponentsV2()
                    .queue();
            return;
        }
        Optional<Tag> tag = this.tagService.findTag(guildId, target.tagId());
        if (tag.isEmpty()) {
            this.replyNotFound(event, locale);
            return;
        }
        switch (target.action()) {
            case TagViews.MANAGE -> event.editComponents(TagViews.configCard(tag.get(), locale, false))
                    .useComponentsV2()
                    .queue();
            case TagViews.DELETE -> event.editComponents(TagViews.confirmDeleteCard(tag.get(), locale))
                    .useComponentsV2()
                    .queue();
            case TagViews.CONFIRM_DELETE -> {
                if (!this.tagService.deleteTag(guildId, tag.get().id())) {
                    this.replyNotFound(event, locale);
                    return;
                }
                event.editComponents(TagViews.deletedCard(tag.get(), locale)).useComponentsV2().queue();
                log.info("Tag deleted: guild={}, tag={}, by={}", guildId, tag.get().id(), member.getIdLong());
            }
            case TagViews.AGE -> event.replyModal(this.daysModal(tag.get(), TagViews.AGE,
                    Messages.get(locale, "tag.age.title"), Messages.get(locale, "tag.modal.age.label"),
                    tag.get().conditions().minAccountAgeDays(), locale)).queue();
            case TagViews.SENIORITY -> event.replyModal(this.daysModal(tag.get(), TagViews.SENIORITY,
                    Messages.get(locale, "tag.seniority.title"), Messages.get(locale, "tag.modal.seniority.label"),
                    tag.get().conditions().minMemberDays(), locale)).queue();
            case TagViews.ACTIVITY -> event.replyModal(this.activityModal(tag.get(), locale)).queue();
            case TagViews.DONE -> event.editComponents(TagViews.savedCard(tag.get(), locale))
                    .useComponentsV2()
                    .queue();
            default -> {
            }
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        ComponentTarget target = parseTarget(event.getModalId(), MODAL_PREFIX);
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (target == null || guild == null || member == null) {
            return;
        }
        Locale locale = this.guildConfigService.language(guild.getIdLong());
        if (!this.isAdmin(member)) {
            this.replyError(event, Messages.get(locale, "error.title"), Messages.get(locale, "error.admin_required"));
            return;
        }
        long guildId = guild.getIdLong();
        Optional<Tag> updated;
        try {
            updated = switch (target.action()) {
                case TagViews.AGE -> this.tagService.setMinAccountAge(guildId, target.tagId(),
                        ModalInputs.parseNumber(event.getValue(VALUE_INPUT), ModalInputs.MAX_DAYS));
                case TagViews.SENIORITY -> this.tagService.setMinMemberDays(guildId, target.tagId(),
                        ModalInputs.parseNumber(event.getValue(VALUE_INPUT), ModalInputs.MAX_DAYS));
                case TagViews.ACTIVITY -> this.tagService.setActivity(guildId, target.tagId(),
                        ModalInputs.parseNumber(event.getValue(MESSAGES_INPUT), ModalInputs.MAX_MESSAGES),
                        ModalInputs.parsePeriod(event.getValue(PERIOD_INPUT)));
                default -> Optional.empty();
            };
        } catch (IllegalArgumentException e) {
            int max = target.action().equals(TagViews.ACTIVITY) ? ModalInputs.MAX_MESSAGES : ModalInputs.MAX_DAYS;
            this.replyError(event, Messages.get(locale, "error.invalid_title"),
                    Messages.format(locale, "tag.error.number_invalid", max));
            return;
        }
        this.showUpdatedCard(event, updated, locale);
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        ComponentTarget target = parseTarget(event.getComponentId(), TagViews.COMPONENT_PREFIX);
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (target == null || guild == null || member == null) {
            return;
        }
        Locale locale = this.guildConfigService.language(guild.getIdLong());
        if (!this.isAdmin(member)) {
            this.replyError(event, Messages.get(locale, "error.title"), Messages.get(locale, "error.admin_required"));
            return;
        }
        Set<Long> roleIds = event.getMentions().getRoles().stream()
                .filter(role -> !role.isPublicRole())
                .map(Role::getIdLong)
                .collect(Collectors.toSet());
        long guildId = guild.getIdLong();
        Optional<Tag> updated = switch (target.action()) {
            case TagViews.REQUIRED -> this.tagService.setRequiredRoles(guildId, target.tagId(), roleIds);
            case TagViews.FORBIDDEN -> this.tagService.setForbiddenRoles(guildId, target.tagId(), roleIds);
            default -> Optional.empty();
        };
        if (updated.isEmpty()) {
            this.replyNotFound(event, locale);
            return;
        }
        event.editComponents(TagViews.configCard(updated.get(), locale, false)).useComponentsV2().queue();
    }

    private void createTag(SlashCommandInteractionEvent event, Guild guild, Member member, String name, String emoji,
            Locale locale) {
        switch (this.tagService.createTag(guild.getIdLong(), name, emoji)) {
            case TagService.Created created -> {
                event.replyComponents(TagViews.configCard(created.tag(), locale, true))
                        .useComponentsV2()
                        .setEphemeral(true)
                        .queue();
                log.info("Tag created: guild={}, tag={}, by={}", guild.getIdLong(), created.tag().id(),
                        member.getIdLong());
            }
            case TagService.NameTaken _ -> this.replyError(event, Messages.get(locale, "error.invalid_title"),
                    Messages.format(locale, "tag.error.name_taken", name.strip()));
            case TagService.InvalidName _ -> this.replyError(event, Messages.get(locale, "error.invalid_title"),
                    Messages.format(locale, "tag.error.name_invalid", TagService.MAX_NAME_LENGTH));
        }
    }

    private void showUpdatedCard(ModalInteractionEvent event, Optional<Tag> updated, Locale locale) {
        if (updated.isEmpty()) {
            this.replyNotFound(event, locale);
            return;
        }
        event.editComponents(TagViews.configCard(updated.get(), locale, false)).useComponentsV2().queue();
    }

    private Modal daysModal(Tag tag, String action, String title, String label, Integer current, Locale locale) {
        return Modal.create(MODAL_PREFIX + action + ":" + tag.id(), title)
                .addComponents(Label.of(label, Messages.get(locale, "tag.modal.zero_hint"),
                        ModalInputs.numberInput(VALUE_INPUT, current)))
                .build();
    }

    private Modal activityModal(Tag tag, Locale locale) {
        MemberConditions conditions = tag.conditions();
        return Modal.create(MODAL_PREFIX + TagViews.ACTIVITY + ":" + tag.id(),
                        Messages.get(locale, "tag.activity.title"))
                .addComponents(
                        Label.of(Messages.get(locale, "tag.modal.activity.messages"),
                                Messages.get(locale, "tag.modal.zero_hint"),
                                ModalInputs.numberInput(MESSAGES_INPUT, conditions.minMessages())),
                        Label.of(Messages.get(locale, "tag.modal.activity.period"),
                                ModalInputs.periodSelect(PERIOD_INPUT, conditions.activityPeriod(), locale)))
                .build();
    }

    private boolean isAdmin(Member member) {
        return AdminAccess.isAdmin(this.guildConfigService, member);
    }

    private void replyInvalidEmoji(IReplyCallback event, String input, Locale locale) {
        this.replyError(event, Messages.get(locale, "error.invalid_title"),
                Messages.format(locale, "tag.error.emoji_invalid", input));
    }

    private void replyNotFound(IReplyCallback event, Locale locale) {
        this.replyError(event, Messages.get(locale, "error.invalid_title"),
                Messages.get(locale, "tag.error.not_found"));
    }

    private void replyError(IReplyCallback event, String title, String description) {
        event.replyComponents(Cards.simple(Cards.ERROR_COLOR, title, description))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }

    private static ComponentTarget parseTarget(String id, String prefix) {
        if (!id.startsWith(prefix)) {
            return null;
        }
        String[] parts = id.substring(prefix.length()).split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new ComponentTarget(parts[0], Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
