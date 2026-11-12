<div align="center">

# Match People

**A configurable, double-blind matchmaking bot for Discord.**

[![CI](https://github.com/sachachtrl/matchppl/actions/workflows/ci.yml/badge.svg)](https://github.com/sachachtrl/matchppl/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![JDA](https://img.shields.io/badge/JDA-6.7-5865F2?logo=discord&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-003B57?logo=sqlite&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?logo=apachemaven&logoColor=white)
![JUnit](https://img.shields.io/badge/tested%20with-JUnit%206-25A162?logo=junit5&logoColor=white)
![Status](https://img.shields.io/badge/status-work%20in%20progress-yellow)

</div>

Match People suggests members of a server to each other, one person at a time, based on the tags they have in common. Both people receive the suggestion privately and decide on their own: nobody ever learns the other's answer. Only when both accept does the bot open a temporary private text and voice channel for them.

What the matching is *for* is up to each server — finding teammates, planning outings, meeting people — so admins configure everything: tags, access conditions and timings.

> **Status: work in progress.** Matching, proposals, private channels and crash recovery work end to end.

## Features

### Available

- **`/match`** — a single private card to manage your profile: enable or disable your search, write a short introduction with a live preview, pick your tags (only the ones you are allowed to use), exclude tags you never want to be matched with, and switch to anonymous mode. An advanced card adds required tags, a minimum number of tags in common, a minimum compatibility, the option to exclude anonymous members or accounts with a default avatar, and conditions on the other person. Tag menus are split into pages of 25 when a server has more tags, and saving one page never touches the tags chosen on the others.
- **`/setadmin <role>`** / **`/removeadmin <role>`** / **`/listadmin`** — choose which roles may use the bot's admin commands (requires the *Manage Server* permission). `/listadmin` shows them in a card where roles can be removed or added in bulk; every click re-reads the database, so the card always reflects the real state even when another command changed it.
- **`/newtag <name> <emoji>`** — create an independent tag (a game, a city…) and configure its access conditions in place: minimum account age, time on the server, recent activity, required and forbidden roles. Every change is saved instantly.
- **`/tags`** — browse the server's tags page by page, open any of them in the same configuration card, and delete them after confirmation.
- **Matching** — every minute, the bot pairs searching members server-wide. The score is the number of shared tags divided by the larger tag count, so it is identical on both sides; every filter is checked in both directions, the best scores are served first, and ties go to whoever has been waiting the longest.
- **Double-blind proposals** — sent privately to both members at once, with *Skip* and *Accept* buttons and a response deadline. Accepting can be cancelled, and nobody is ever told the other person's answer: a refusal is indistinguishable from no answer. If either member is anonymous, neither sees the other's name or avatar. A pair that ended without a match is never proposed again.
- **`/reset <user>`** / **`/resetpair <user> <other>`** / **`/resetall`** — let admins lift those restrictions for one member, for a single pair of members, or for the whole server, behind a clear confirmation card. The history itself is kept.
- **Private channels** — when both members accept, the bot opens a private text and voice channel that only they can see, named after the server's prefix and the lowest free number (*Match #1*, *Match #2*…). Both members stay out of matchmaking while it exists; either of them can end it early, and it is deleted automatically when its lifetime ends, with a reminder an hour before. Members who prefer not to get a channel can turn it off in `/match`: they receive each other's profile by direct message instead. A match always reveals both identities, even in anonymous mode.
- **Reports** — every channel and every match without a channel has a *Report a problem* button. Reports go to the server's log channel, mentioning the bot's admin roles, or by direct message to members with the *Manage Server* permission when no log channel is set. The other member is never told.
- **`/config`** — server settings for admins in a single card: response delay, matching frequency, private channels on or off, channel lifetime, name prefix, category and log channel. It can also turn off every member's search at once, behind a clear confirmation.
- **Crash recovery** — every deadline is stored as an absolute UTC instant, so expired proposals and channels are caught up automatically after a restart. A heartbeat written every minute also detects downtime longer than five minutes: proposals that were in progress are then interrupted (members' cards say so, and it does not count as a refusal), while a *Skip* already given still counts.
- **Activity tracking** — message counts per member, used by tag access conditions and the conditions members set on each other: daily counts for the last 30 days (purged every day) and an all-time total. The bot never reads message content.
- **English and French** — admins choose the server's language in `/config`; every card, direct message and channel message follows it. Texts live in resource bundles, and a test checks that every translation has the same keys and placeholders as the English one.
- **Modern message layout** — replies use Discord's Components V2: cards with sections, separators and inline buttons.

### Planned

- **Profile photos** — uploading a picture in `/match`, stored securely by the bot (Discord attachment links expire), once a safe hosting setup is in place.

## Tech stack

- **Java 25**
- **[JDA 6](https://github.com/discord-jda/JDA)** (Java Discord API)
- **SQLite** through JDBC
- **Maven**
- **SLF4J** + **Logback** for logging
- **JUnit 6** for tests

## Architecture

The code is split into layers, each one only talking to the layer below it:

```
discord/      Layer 1 — JDA listeners: commands, buttons, message events, and the
              Components V2 layouts sent back to Discord.
              Translates Discord events into plain values and never makes decisions.
service/      Layer 2 — business rules. Does not depend on JDA, so it can be unit tested.
repository/   Layer 3 — data access. The only place containing SQL.
scheduler/    Layer 4 — the periodic task: records a heartbeat, closes expired proposals
              and channels, sends channel reminders, then runs a matchmaking round
              on every server through layer 2.
```

Services depend on repository **interfaces**, and every dependency is injected through constructors. Storage can therefore change without touching the business rules: activity tracking moved from an in-memory map to SQLite without modifying a single line of its service or listener.

## Getting started

### Prerequisites

- JDK 25
- Maven 3.9+
- A Discord application with a bot user, created on the [Discord Developer Portal](https://discord.com/developers/applications), invited to a server with the `bot` and `applications.commands` scopes
- The bot role needs the **Manage Channels** and **Manage Roles** permissions to create the private channels, and **Mention All Roles** if the admin roles mentioned in reports are not mentionable
- The **Server Members Intent** enabled in the portal (*Bot* → *Privileged Gateway Intents*); without it, the bot refuses to start

### Run

1. Clone the repository.
2. Copy `.env.example` to `.env` and fill in your bot token and an encryption key for member introductions:
   ```
   TOKEN=your_bot_token
   PROFILE_KEY=32_random_bytes_in_base64
   ```
   Generate the key with `openssl rand -base64 32`, and keep a copy somewhere safe: without it, stored introductions can no longer be read.
3. Start the bot:
   ```bash
   mvn
   ```

The SQLite database (`matchppl.db`) is created automatically on first launch.

### Tests

```bash
mvn test
```

## Privacy

- The bot requests the `GUILD_MESSAGES` intent to count messages, and the privileged `GUILD_MEMBERS` intent to check roles, server seniority and avatars when matching, and to notice when a member leaves. It does **not** use the privileged *Message Content* intent and never reads what members write.
- Profiles are **per server** and cannot be copied from one server to another. A member's profile and message counts are **deleted** as soon as they leave the server.
- When the bot is kicked or banned from a server, **everything it stored about that server is deleted**: profiles, tags, settings, activity, proposals and channel records.
- Member introductions are **encrypted at rest** with AES-256-GCM. The key lives outside the database, so a leaked database file alone reveals none of them.
- Excluded tags are private: the other person is never told why they were not proposed.
- Reports are only sent to the server's admins and are not stored by the bot.
- Message activity is reduced to numbers: daily counts are kept for 30 days only, next to a single all-time total per member.
- The bot stores no images.

## License

Distributed under the [MIT License](LICENSE).
