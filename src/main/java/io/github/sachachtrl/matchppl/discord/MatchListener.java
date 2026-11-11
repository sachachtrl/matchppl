package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.MemberSnapshot;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.service.ProfileService;
import io.github.sachachtrl.matchppl.service.TagService;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(MatchListener.class);

    private static final String COMMAND_NAME = "match";
    private static final String MODAL_PREFIX = "profilemodal:";
    private static final String PRESENTATION_MODAL = MODAL_PREFIX + "presentation";
    private static final String COMPATIBILITY_MODAL = MODAL_PREFIX + "compatibility";
    private static final String CONDITIONS_MODAL = MODAL_PREFIX + "conditions";
    private static final String TEXT_INPUT = "text";
    private static final String VALUE_INPUT = "value";
    private static final String AGE_INPUT = "age";
    private static final String SENIORITY_INPUT = "seniority";
    private static final String MESSAGES_INPUT = "messages";
    private static final String PERIOD_INPUT = "period";
    private static final int COMPATIBILITY_INPUT_LENGTH = 3;

    private final ProfileService profileService;
    private final TagService tagService;

    public MatchListener(ProfileService profileService, TagService tagService) {
        this.profileService = profileService;
        this.tagService = tagService;
    }

    public static SlashCommandData command() {
        return Commands.slash(COMMAND_NAME, Messages.get(Messages.DEFAULT_LOCALE, "command.match.description"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (!event.getName().equals(COMMAND_NAME) || member == null) {
            return;
        }
        event.replyComponents(this.mainView(member, Messages.DEFAULT_LOCALE))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        Member member = event.getMember();
        if (!id.startsWith(ProfileViews.PREFIX) || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        long guildId = member.getGuild().getIdLong();
        long userId = member.getIdLong();
        switch (id) {
            case ProfileViews.SEARCH_ON, ProfileViews.SEARCH_OFF -> {
                boolean searching = id.equals(ProfileViews.SEARCH_ON);
                if (this.profileService.setSearching(guildId, userId, searching)) {
                    log.debug("Search state changed: guild={}, user={}, searching={}", guildId, userId, searching);
                }
                this.edit(event, this.mainView(member, locale));
            }
            case ProfileViews.ANONYMOUS_ON, ProfileViews.ANONYMOUS_OFF -> {
                this.profileService.setAnonymous(guildId, userId, id.equals(ProfileViews.ANONYMOUS_ON));
                this.edit(event, this.mainView(member, locale));
            }
            case ProfileViews.EXCLUDE_ANONYMOUS_ON, ProfileViews.EXCLUDE_ANONYMOUS_OFF -> {
                this.profileService.setExcludeAnonymous(guildId, userId,
                        id.equals(ProfileViews.EXCLUDE_ANONYMOUS_ON));
                this.edit(event, this.advancedView(member, locale));
            }
            case ProfileViews.EXCLUDE_AVATARS_ON, ProfileViews.EXCLUDE_AVATARS_OFF -> {
                this.profileService.setExcludeDefaultAvatars(guildId, userId,
                        id.equals(ProfileViews.EXCLUDE_AVATARS_ON));
                this.edit(event, this.advancedView(member, locale));
            }
            case ProfileViews.ADVANCED -> this.edit(event, this.advancedView(member, locale));
            case ProfileViews.MAIN -> this.edit(event, this.mainView(member, locale));
            case ProfileViews.PRESENTATION -> event.replyModal(this.presentationModal(guildId, userId, locale)).queue();
            case ProfileViews.COMPATIBILITY -> event.replyModal(this.compatibilityModal(guildId, userId, locale)).queue();
            case ProfileViews.CONDITIONS -> event.replyModal(this.conditionsModal(guildId, userId, locale)).queue();
            default -> {
            }
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        Member member = event.getMember();
        if (!id.startsWith(ProfileViews.PREFIX) || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        long guildId = member.getGuild().getIdLong();
        long userId = member.getIdLong();
        if (id.equals(ProfileViews.MIN_COMMON_TAGS)) {
            this.profileService.setMinCommonTags(guildId, userId, Integer.parseInt(event.getValues().getFirst()));
            this.edit(event, this.advancedView(member, locale));
            return;
        }
        Set<Long> tagIds = event.getValues().stream().map(Long::parseLong).collect(Collectors.toSet());
        switch (id) {
            case ProfileViews.TAGS -> {
                this.profileService.setTags(snapshot(member), tagIds);
                this.edit(event, this.mainView(member, locale));
            }
            case ProfileViews.EXCLUDED -> {
                this.profileService.setExcludedTags(guildId, userId, tagIds);
                this.edit(event, this.mainView(member, locale));
            }
            case ProfileViews.REQUIRED -> {
                this.profileService.setRequiredTags(guildId, userId, tagIds);
                this.edit(event, this.advancedView(member, locale));
            }
            default -> {
            }
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        Member member = event.getMember();
        if (!id.startsWith(MODAL_PREFIX) || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        long guildId = member.getGuild().getIdLong();
        long userId = member.getIdLong();
        switch (id) {
            case PRESENTATION_MODAL -> {
                ModalMapping text = event.getValue(TEXT_INPUT);
                if (!this.profileService.setPresentation(guildId, userId, text == null ? null : text.getAsString())) {
                    this.replyError(event, Messages.format(locale, "error.presentation_too_long",
                            ProfileService.MAX_PRESENTATION_LENGTH), locale);
                    return;
                }
                this.edit(event, this.mainView(member, locale));
            }
            case COMPATIBILITY_MODAL -> {
                try {
                    Integer percent = ModalInputs.parseNumber(event.getValue(VALUE_INPUT), ProfileService.MAX_COMPATIBILITY);
                    this.profileService.setMinCompatibility(guildId, userId, percent);
                } catch (IllegalArgumentException e) {
                    this.replyError(event, Messages.get(locale, "error.compat_invalid"), locale);
                    return;
                }
                this.edit(event, this.advancedView(member, locale));
            }
            case CONDITIONS_MODAL -> {
                try {
                    this.profileService.setPartnerConditions(guildId, userId,
                            ModalInputs.parseNumber(event.getValue(AGE_INPUT), ModalInputs.MAX_DAYS),
                            ModalInputs.parseNumber(event.getValue(SENIORITY_INPUT), ModalInputs.MAX_DAYS),
                            ModalInputs.parseNumber(event.getValue(MESSAGES_INPUT), ModalInputs.MAX_MESSAGES),
                            ModalInputs.parsePeriod(event.getValue(PERIOD_INPUT)));
                } catch (IllegalArgumentException e) {
                    this.replyError(event, Messages.format(locale, "tag.error.number_invalid", ModalInputs.MAX_DAYS),
                            locale);
                    return;
                }
                this.edit(event, this.advancedView(member, locale));
            }
            default -> {
            }
        }
    }

    private Container mainView(Member member, Locale locale) {
        long guildId = member.getGuild().getIdLong();
        Profile profile = this.profileService.getProfile(guildId, member.getIdLong());
        return ProfileViews.mainCard(profile, this.tagService.usableTags(snapshot(member)),
                this.tagService.listTags(guildId), locale);
    }

    private Container advancedView(Member member, Locale locale) {
        long guildId = member.getGuild().getIdLong();
        Profile profile = this.profileService.getProfile(guildId, member.getIdLong());
        return ProfileViews.advancedCard(profile, this.tagService.listTags(guildId), locale);
    }

    private Modal presentationModal(long guildId, long userId, Locale locale) {
        Profile profile = this.profileService.getProfile(guildId, userId);
        TextInput.Builder input = TextInput.create(TEXT_INPUT, TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setMaxLength(ProfileService.MAX_PRESENTATION_LENGTH);
        if (profile.presentation() != null) {
            input.setValue(profile.presentation());
        }
        return Modal.create(PRESENTATION_MODAL, Messages.get(locale, "modal.presentation.title"))
                .addComponents(Label.of(Messages.get(locale, "modal.presentation.label"),
                        Messages.format(locale, "modal.presentation.hint", ProfileService.MAX_PRESENTATION_LENGTH),
                        input.build()))
                .build();
    }

    private Modal compatibilityModal(long guildId, long userId, Locale locale) {
        Profile profile = this.profileService.getProfile(guildId, userId);
        TextInput input = TextInput.create(VALUE_INPUT, TextInputStyle.SHORT)
                .setRequired(false)
                .setMaxLength(COMPATIBILITY_INPUT_LENGTH)
                .setPlaceholder("0")
                .setValue(profile.preferences().minCompatibility() == null
                        ? null
                        : String.valueOf(profile.preferences().minCompatibility()))
                .build();
        return Modal.create(COMPATIBILITY_MODAL, Messages.get(locale, "modal.compat.title"))
                .addComponents(Label.of(Messages.format(locale, "modal.compat.label"),
                        Messages.get(locale, "tag.modal.zero_hint"), input))
                .build();
    }

    private Modal conditionsModal(long guildId, long userId, Locale locale) {
        MemberConditions conditions = this.profileService.getProfile(guildId, userId).preferences().conditions();
        String zeroHint = Messages.get(locale, "tag.modal.zero_hint");
        return Modal.create(CONDITIONS_MODAL, Messages.get(locale, "modal.conditions.title"))
                .addComponents(
                        Label.of(Messages.get(locale, "tag.modal.age.label"), zeroHint,
                                ModalInputs.numberInput(AGE_INPUT, conditions.minAccountAgeDays())),
                        Label.of(Messages.get(locale, "tag.modal.seniority.label"), zeroHint,
                                ModalInputs.numberInput(SENIORITY_INPUT, conditions.minMemberDays())),
                        Label.of(Messages.get(locale, "tag.modal.activity.messages"), zeroHint,
                                ModalInputs.numberInput(MESSAGES_INPUT, conditions.minMessages())),
                        Label.of(Messages.get(locale, "tag.modal.activity.period"),
                                ModalInputs.periodSelect(PERIOD_INPUT, conditions.activityPeriod(), locale)))
                .build();
    }

    private void edit(IMessageEditCallback event, Container view) {
        event.editComponents(view).useComponentsV2().queue();
    }

    private void replyError(IReplyCallback event, String description, Locale locale) {
        event.replyComponents(Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.invalid_title"), description))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }

    private static MemberSnapshot snapshot(Member member) {
        return new MemberSnapshot(
                member.getGuild().getIdLong(),
                member.getIdLong(),
                member.getUser().getTimeCreated().toInstant(),
                member.getTimeJoined().toInstant(),
                member.getRoles().stream().map(Role::getIdLong).collect(Collectors.toSet()));
    }
}
