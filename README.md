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

> **Status: work in progress.** The core infrastructure is in place; the matching engine is under development.

## Features

### Available

- **`/match`** — a single private card to manage your profile: enable or disable your search, write a short introduction with a live preview, pick your tags (only the ones you are allowed to use), exclude tags you never want to be matched with, and switch to anonymous mode. An advanced card adds required tags, a minimum compatibility, a photo requirement and conditions on the other person.
- **`/setadmin <role>`** / **`/removeadmin <role>`** / **`/listadmin`** — choose which roles may use the bot's admin commands (requires the *Manage Server* permission). `/listadmin` shows them in a card where roles can be removed or added in bulk; every click re-reads the database, so the card always reflects the real state even when another command changed it.
- **`/newtag <name> <emoji>`** — create an independent tag (a game, a city…) and configure its access conditions in place: minimum account age, time on the server, recent activity, required and forbidden roles. Every change is saved instantly.
- **`/tags`** — browse the server's tags page by page, open any of them in the same configuration card, and delete them after confirmation.
- **Activity tracking** — daily message counts per member, used later for tag access conditions. The bot never reads message content.
- **Translatable interface** — every user-facing text lives in resource bundles, with English as the default language.
- **Modern message layout** — replies use Discord's Components V2: cards with sections, separators and inline buttons.

### Planned

- **Matching** — the score is the number of shared tags divided by the larger tag count, so it is identical on both sides. The best available score is always proposed first.
- **Double-blind proposals** — sent privately to both members at once, with *Skip* and *Accept* buttons and a response deadline. A refusal is indistinguishable from no answer.
- **Temporary channels** — a private text and voice channel for each mutual match, deleted when its lifetime ends.
- **Crash resilience** — every deadline is stored as an absolute UTC instant and processed by a periodic check, so nothing is lost if the bot goes down.
- **`/language`** — change the bot's language for a whole server.

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
```

Services depend on repository **interfaces**, and every dependency is injected through constructors. Storage can therefore change without touching the business rules: activity tracking moved from an in-memory map to SQLite without modifying a single line of its service or listener.

## Getting started

### Prerequisites

- JDK 25
- Maven 3.9+
- A Discord application with a bot user, created on the [Discord Developer Portal](https://discord.com/developers/applications), invited to a server with the `bot` and `applications.commands` scopes

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

- The bot only requests the `GUILD_MESSAGES` intent, to count messages. It does **not** use the privileged *Message Content* intent and never reads what members write.
- Profiles are **per server** and cannot be copied from one server to another.
- Member introductions are **encrypted at rest** with AES-256-GCM. The key lives outside the database, so a leaked database file alone reveals none of them.
- Excluded tags are private: the other person is never told why they were not proposed.
- The bot stores no images.

## License

Distributed under the [MIT License](LICENSE).
