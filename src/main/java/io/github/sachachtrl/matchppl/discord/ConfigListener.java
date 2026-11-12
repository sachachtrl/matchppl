package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.GuildSettings;
import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.ProfileService;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
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

public class ConfigListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ConfigListener.class);

    private static final String COMMAND_NAME = "config";
    private static final String MODAL_PREFIX = "configmodal:";
    private static final String VALUE_INPUT = "value";
    private static final int DURATION_INPUT_LENGTH = 16;

    private final GuildConfigService guildConfigService;
    private final ProfileService profileService;

    public ConfigListener(GuildConfigService guildConfigService, ProfileService profileService) {
        this.guildConfigService = guildConfigService;
        this.profileService = profileService;
    }

    public static SlashCommandData command() {
        return Commands.slash(COMMAND_NAME, Messages.get(Messages.DEFAULT_LOCALE, "command.config.description"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!event.getName().equals(COMMAND_NAME) || guild == null || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        if (!this.checkAdmin(event, member, locale)) {
            return;
        }
        event.replyComponents(this.view(guild, locale)).useComponentsV2().setEphemeral(true).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!id.startsWith(ConfigViews.PREFIX) || guild == null || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        if (!this.checkAdmin(event, member, locale)) {
            return;
        }
        GuildSettings settings = this.guildConfigService.settings(guild.getIdLong());
        switch (id) {
            case ConfigViews.RESPONSE_DELAY -> event.replyModal(this.durationModal(id,
                    "config.response_delay.title", settings.responseDelay(), locale)).queue();
            case ConfigViews.MATCHING_INTERVAL -> event.replyModal(this.durationModal(id,
                    "config.matching_interval.title", settings.matchingInterval(), locale)).queue();
            case ConfigViews.ROOM_LIFETIME -> event.replyModal(this.durationModal(id,
                    "config.room_lifetime.title", settings.roomLifetime(), locale)).queue();
            case ConfigViews.ROOM_PREFIX -> event.replyModal(this.prefixModal(settings.roomPrefix(), locale)).queue();
            case ConfigViews.ROOMS_ON, ConfigViews.ROOMS_OFF -> {
                boolean enabled = id.equals(ConfigViews.ROOMS_ON);
                this.guildConfigService.setRoomsEnabled(guild.getIdLong(), enabled);
                this.logChange(guild, member, "roomsEnabled", String.valueOf(enabled));
                this.refresh(event, guild, locale);
            }
            case ConfigViews.STOP_ALL -> event.editComponents(ConfigViews.stopAllConfirmation(
                    this.profileService.countSearching(guild.getIdLong()), locale)).useComponentsV2().queue();
            case ConfigViews.STOP_ALL_CONFIRM -> {
                int stopped = this.profileService.stopAllSearches(guild.getIdLong());
                this.logChange(guild, member, "stopAllSearches", String.valueOf(stopped));
                event.editComponents(ConfigViews.stopAllDone(stopped, locale)).useComponentsV2().queue();
            }
            case ConfigViews.BACK -> this.refresh(event, guild, locale);
            default -> {
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String id = event.getComponentId();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!id.startsWith(ConfigViews.PREFIX) || guild == null || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        if (!this.checkAdmin(event, member, locale)) {
            return;
        }
        List<GuildChannel> channels = event.getMentions().getChannels();
        Long channelId = channels.isEmpty() ? null : channels.getFirst().getIdLong();
        switch (id) {
            case ConfigViews.CATEGORY -> this.guildConfigService.setCategory(guild.getIdLong(), channelId);
            case ConfigViews.LOG_CHANNEL -> this.guildConfigService.setLogChannel(guild.getIdLong(), channelId);
            default -> {
                return;
            }
        }
        this.logChange(guild, member, id.substring(ConfigViews.PREFIX.length()), String.valueOf(channelId));
        this.refresh(event, guild, locale);
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!modalId.startsWith(MODAL_PREFIX) || guild == null || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        if (!this.checkAdmin(event, member, locale)) {
            return;
        }
        String setting = ConfigViews.PREFIX + modalId.substring(MODAL_PREFIX.length());
        ModalMapping value = event.getValue(VALUE_INPUT);
        String input = value == null ? "" : value.getAsString();
        long guildId = guild.getIdLong();
        boolean saved;
        if (setting.equals(ConfigViews.ROOM_PREFIX)) {
            saved = this.guildConfigService.setRoomPrefix(guildId, input);
            if (!saved) {
                this.replyError(event, Messages.format(locale, "config.error.prefix",
                        GuildConfigService.MAX_ROOM_PREFIX_LENGTH), locale);
                return;
            }
        } else {
            Duration duration;
            try {
                duration = DurationText.parse(input);
            } catch (IllegalArgumentException e) {
                duration = null;
            }
            saved = duration != null && switch (setting) {
                case ConfigViews.RESPONSE_DELAY -> this.guildConfigService.setResponseDelay(guildId, duration);
                case ConfigViews.MATCHING_INTERVAL -> this.guildConfigService.setMatchingInterval(guildId, duration);
                case ConfigViews.ROOM_LIFETIME -> this.guildConfigService.setRoomLifetime(guildId, duration);
                default -> false;
            };
            if (!saved) {
                this.replyError(event, this.durationError(setting, locale), locale);
                return;
            }
        }
        this.logChange(guild, member, setting.substring(ConfigViews.PREFIX.length()), input.strip());
        this.refresh(event, guild, locale);
    }

    private Modal durationModal(String settingId, String titleKey, Duration current, Locale locale) {
        TextInput input = TextInput.create(VALUE_INPUT, TextInputStyle.SHORT)
                .setRequired(true)
                .setMaxLength(DURATION_INPUT_LENGTH)
                .setValue(DurationText.toInput(current))
                .build();
        return Modal.create(MODAL_PREFIX + settingId.substring(ConfigViews.PREFIX.length()),
                        Messages.get(locale, titleKey))
                .addComponents(Label.of(Messages.get(locale, titleKey),
                        Messages.get(locale, "config.modal.duration.hint"), input))
                .build();
    }

    private Modal prefixModal(String current, Locale locale) {
        TextInput input = TextInput.create(VALUE_INPUT, TextInputStyle.SHORT)
                .setRequired(false)
                .setMaxLength(GuildConfigService.MAX_ROOM_PREFIX_LENGTH)
                .setValue(current)
                .build();
        return Modal.create(MODAL_PREFIX + ConfigViews.ROOM_PREFIX.substring(ConfigViews.PREFIX.length()),
                        Messages.get(locale, "config.modal.prefix.title"))
                .addComponents(Label.of(Messages.get(locale, "config.modal.prefix.label"),
                        Messages.get(locale, "config.modal.prefix.hint"), input))
                .build();
    }

    private String durationError(String setting, Locale locale) {
        Duration min;
        Duration max;
        switch (setting) {
            case ConfigViews.RESPONSE_DELAY -> {
                min = GuildConfigService.MIN_RESPONSE_DELAY;
                max = GuildConfigService.MAX_RESPONSE_DELAY;
            }
            case ConfigViews.MATCHING_INTERVAL -> {
                min = GuildConfigService.MIN_MATCHING_INTERVAL;
                max = GuildConfigService.MAX_MATCHING_INTERVAL;
            }
            default -> {
                min = GuildConfigService.MIN_ROOM_LIFETIME;
                max = GuildConfigService.MAX_ROOM_LIFETIME;
            }
        }
        return Messages.format(locale, "config.error.duration", DurationText.format(min, locale),
                DurationText.format(max, locale));
    }

    private Container view(Guild guild, Locale locale) {
        long guildId = guild.getIdLong();
        return ConfigViews.card(this.guildConfigService.settings(guildId), this.profileService.countSearching(guildId),
                locale);
    }

    private void refresh(IMessageEditCallback event, Guild guild, Locale locale) {
        event.editComponents(this.view(guild, locale)).useComponentsV2().queue();
    }

    private void logChange(Guild guild, Member member, String setting, String value) {
        log.info("Server setting changed: guild={}, setting={}, value={}, by={}",
                guild.getIdLong(), setting, value, member.getIdLong());
    }

    private void replyError(IReplyCallback event, String description, Locale locale) {
        event.replyComponents(Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.invalid_title"), description))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
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
