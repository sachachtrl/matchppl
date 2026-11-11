package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.service.ProfileService;

import java.util.Locale;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(MatchListener.class);

    private static final String COMMAND_NAME = "match";
    private static final String ENABLE_BUTTON_ID = "match:enable";
    private static final String DISABLE_BUTTON_ID = "match:disable";
    private static final int ENABLED_COLOR = 0x57F287;
    private static final int DISABLED_COLOR = 0xED4245;

    private final ProfileService profileService;

    public MatchListener(ProfileService profileService) {
        this.profileService = profileService;
    }

    public static SlashCommandData command() {
        return Commands.slash(COMMAND_NAME, Messages.get(Messages.DEFAULT_LOCALE, "command.match.description"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (!event.getName().equals(COMMAND_NAME) || guild == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        boolean searching = this.profileService.isSearching(guild.getIdLong(), event.getUser().getIdLong());
        event.replyComponents(this.buildView(searching, locale))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        Guild guild = event.getGuild();
        if ((!buttonId.equals(ENABLE_BUTTON_ID) && !buttonId.equals(DISABLE_BUTTON_ID)) || guild == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        long guildId = guild.getIdLong();
        long userId = event.getUser().getIdLong();
        boolean searching = buttonId.equals(ENABLE_BUTTON_ID);
        this.profileService.setSearching(guildId, userId, searching);
        event.editComponents(this.buildView(searching, locale))
                .useComponentsV2()
                .queue();
        log.debug("Search state changed: guild={}, user={}, searching={}", guildId, userId, searching);
    }

    private Container buildView(boolean searching, Locale locale) {
        String statusKey = searching ? "match.status.enabled" : "match.status.disabled";
        Button button = searching
                ? Button.danger(DISABLE_BUTTON_ID, Messages.get(locale, "match.button.disable"))
                : Button.success(ENABLE_BUTTON_ID, Messages.get(locale, "match.button.enable"));
        return Container.of(
                TextDisplay.of("## " + Messages.get(locale, "match.title")),
                Separator.createDivider(Separator.Spacing.SMALL),
                Section.of(button, TextDisplay.of(Messages.get(locale, statusKey)))
        ).withAccentColor(searching ? ENABLED_COLOR : DISABLED_COLOR);
    }
}
