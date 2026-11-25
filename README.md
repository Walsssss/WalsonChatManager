# WalsonChatManager

WalsonChatManager is a Paper plugin focused on **Minecraft chat management**. It provides player mutes, chat filtering, global chat control, anti-spam protection, management GUIs, configurable notification sounds, and optional DiscordSRV integration.

The plugin is intentionally limited to chat-related functionality. 

---

## Contents

- [Requirements](#requirements)
- [Features](#features)
- [Installation](#installation)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration files](#configuration-files)
- [DiscordSRV setup](#discordsrv-setup)
- [Offline mute behavior](#offline-mute-behavior)
- [Anti-spam behavior](#anti-spam-behavior)
- [Database and data](#database-and-data)
- [Troubleshooting](#troubleshooting)

---

## Requirements

- A Paper server compatible with the plugin's `api-version: '26.1'` declaration.
- Java 25 for the current development build.
- DiscordSRV is **optional**. Install it only if Discord announcements, staff logs, account linking, and Discord timeout synchronization are required.

> The plugin declares DiscordSRV as a soft dependency. Core Minecraft chat features should remain available without DiscordSRV unless `require-discordsrv` is enabled in `discord.yml`.

---

## Features

### Player mute management

- Mute and unmute players through commands or inventory GUIs.
- Supports timed and permanent mutes.
- Supports offline player mutes.
- Offline mutes remain pending until the player joins. Their mute duration begins when they join, not when staff issues the punishment.
- Players receive a private mute notification when the mute becomes active.
- Mute and unmute notifications can play configurable sounds.
- Normal mutes can be announced publicly.
- Silent mute can be selected in the mute GUI. Silent mutes suppress public Minecraft and Discord announcement-channel messages while retaining moderation and staff logs.
- Mute history is stored in SQLite and viewable through a GUI.
- Automatic mute expiration is handled by the plugin.

### Chat filter

- Configurable filter rules, words/phrases, matching behavior, and priorities.
- Supported match types include `WORD`, `CONTAINS`, and `REGEX` where configured/supported by the current filter implementation.
- Rules can apply multiple actions, including:
  - `BLOCK_MESSAGE`
  - `WARNING`
  - `MUTE`
  - `TEMPORARY_MUTE`
- Staff can manage filter rules through `/chatfilter` and the Chat Filter GUI.
- Filter-bypass permission is available for trusted roles.
- Filter violations are counted and displayed in Mute History.
- A single matched message is counted once even if its rule has multiple actions.
- Filter warnings can play a configurable sound for the affected player.
- Filter violations can be sent to the Discord staff channel when DiscordSRV integration is enabled.

### Global chat control

- Enable or disable global chat with `/muteglobal`.
- Staff can be granted permission to bypass global chat mute.
- Global chat state is persisted in `config.yml`.
- Configurable public announcements and sounds are sent when global chat is disabled or enabled.

### Anti-spam protection

- Detects repeated messages within a configurable rolling time window.
- Blocks messages that exceed the configured message threshold.
- Sends the player a warning when spam is detected.
- Applies a configurable chat cooldown after a spam warning.
- Counts separate spam warnings over a configurable period.
- Automatically mutes a player after the configured number of spam warnings.
- Automatic spam mutes use the existing mute manager, so normal mute notifications, public announcements, staff notifications, mute history, and Discord synchronization can apply.
- Each blocked spam message contributes to the player's chat-filter/chat-violation counter.
- Staff can edit anti-spam settings through the Chat Manager GUI.
- A bypass permission is available.

Default anti-spam settings in the current configuration:

| Setting | Default | Meaning |
|---|---:|---|
| Detection window | 1 second | Rolling period used to count messages |
| Warning threshold | 5 | More than 5 messages triggers a warning and blocks the triggering message |
| Cooldown | 2 seconds | Chat is blocked for this period after a spam warning; `0` disables it |
| Warnings before mute | 4 | Number of separate spam warnings before automatic mute |
| Warning reset period | 24 hours | Spam warnings older than this period are excluded from the warning total |
| Automatic mute duration | `10m` | Default mute duration |
| Automatic mute reason | `Spamming` | Default punishment reason |

### Management GUIs

- `/chatmanager` opens the main management GUI.
- The main GUI provides access to mute, unmute, mute history, chat filter, global chat, anti-spam, sound settings, and reload controls.
- The mute GUI supports duration/reason selection and a silent-announcement option.
- Anti-spam settings can be edited in the GUI.
- Sound settings can be enabled/disabled and configured in the GUI, with preview support.

### Configurable sounds

The current sound events are:

| Event | Recipient |
|---|---|
| Player muted | Muted player only |
| Player unmuted | Unmuted player only |
| Chat-filter warning | Player who triggered the filter |
| Global chat disabled | All online players |
| Global chat enabled | All online players |

Each sound supports an enabled setting, sound identifier, volume, and pitch.

### DiscordSRV integration (optional)

- Sends rich Discord embeds to configured moderation, staff, and announcements channels.
- Supports mute and unmute event messages.
- Supports global chat enabled/disabled announcements and staff logs.
- Sends chat-filter violation logs to the staff channel.
- Silent mutes are still recorded in moderation and staff channels but are omitted from the public announcements channel.
- Links Minecraft accounts to Discord accounts through DiscordSRV's account-link system.
- Synchronizes Minecraft mutes to Discord member timeouts for linked accounts.
- If a player is offline when muted, the Discord timeout is deferred until the player joins the Minecraft server.
- Removes the Discord timeout when the Minecraft mute is removed or expires, subject to the configured synchronization behavior.
- Long and permanent mutes are refreshed because Discord's native timeout has a maximum duration of 28 days.
- Includes DiscordSRV compatibility diagnostics and periodic checks.
- Discord timeout application uses Discord's REST API rather than relying on JDA's `Member.timeoutFor()` method.

---

## Installation

1. Stop the Minecraft server.
2. Back up the current plugin JAR and the entire `plugins/WalsonChatManager/` folder.
3. Place `WalsonChatManager-<version>.jar` in the server's `plugins/` folder.
4. If using Discord integration, install DiscordSRV and configure its bot before enabling the WalsonChatManager Discord features.
5. Start the server once to generate the plugin configuration files.
6. Edit the generated files described in [Configuration files](#configuration-files).
7. Restart the server after changing settings that require a restart. Use `/wcm reload` for supported configuration reloads.

Do not delete `mutes.db` when updating the plugin. It contains active mute records, mute history, and related stored data. Back it up before upgrading.

### Building from source

The project is developed with Gradle, Java 25, and Paper API 26.1.x. From the project root, run:

```powershell
.\gradlew clean build
```

The plugin JAR is normally generated under:

```text
build/libs/WalsonChatManager-<version>.jar
```

---

## Commands

All commands are registered in `plugin.yml`. The permission column lists the permission checked by the command implementation.

| Command | Description | Permission |
|---|---|---|
| `/walsonchatmanager help` or `/wcm help` | Shows the command help | `walsonchatmanager.help` |
| `/walsonchatmanager reload` or `/wcm reload` | Reloads supported plugin configuration files and managers | `walsonchatmanager.reload` |
| `/walsonchatmanager discordstatus` or `/wcm discordstatus` | Displays the latest DiscordSRV compatibility report | `walsonchatmanager.discord.status` |
| `/walsonchatmanager discordcheck` or `/wcm discordcheck` | Runs a fresh DiscordSRV compatibility check | `walsonchatmanager.discord.status` |
| `/mute <player>` | Opens the mute GUI for the target | `walsonchatmanager.mute` |
| `/mute <player> <duration> [reason]` | Applies a timed or permanent mute | `walsonchatmanager.mute` |
| `/unmute <player>` | Opens the unmute GUI for a muted target | `walsonchatmanager.mute` |
| `/unmute <player> [reason]` | Removes a player's mute | `walsonchatmanager.mute` |
| `/mutehistory <player>` | Opens the player's mute history GUI | `walsonchatmanager.mutehistory` |
| `/chatmanager` or `/cm` | Opens the main management GUI | `walsonchatmanager.gui` |
| `/chatfilter` | Opens the Chat Filter GUI | `walsonchatmanager.filter` |
| `/muteglobal` | Toggles global chat mute | `walsonchatmanager.muteglobal` |
| `/muteglobal on` | Disables global chat | `walsonchatmanager.muteglobal` |
| `/muteglobal off` | Enables global chat | `walsonchatmanager.muteglobal` |
| `/muteglobal toggle` | Toggles global chat mute | `walsonchatmanager.muteglobal` |

### Duration examples

The mute duration parser accepts duration values such as:

- `30s` — 30 seconds
- `5m` — 5 minutes
- `2h` — 2 hours
- `1d` — 1 day
- `1w` — 1 week
- `permanent` — permanent mute

Examples:

```text
/mute Steve 10m Spamming
/mute Alex 2h Repeated advertising
/mute Notch permanent Staff decision
/unmute Steve Appeal accepted
/mutehistory Steve
/muteglobal on
/muteglobal off
```

When `/mute <player>` is used without a duration, a player sender is taken to the mute GUI. Console must provide a duration and reason is optional.

---

## Permissions

The current `plugin.yml` declares the following permission nodes. All listed individual permissions default to `op` in the current configuration.

| Permission | Purpose |
|---|---|
| `walsonchatmanager.admin` | Parent permission granting the plugin's listed child permissions |
| `walsonchatmanager.help` | Use WalsonChatManager help |
| `walsonchatmanager.reload` | Reload plugin configuration |
| `walsonchatmanager.mute` | Mute and unmute players; access mute/unmute GUIs |
| `walsonchatmanager.mute.notify` | Receive staff notifications when players are automatically muted |
| `walsonchatmanager.mutehistory` | View mute history |
| `walsonchatmanager.gui` | Open the main Chat Manager GUI |
| `walsonchatmanager.filter` | Open/manage chat filter GUI |
| `walsonchatmanager.muteglobal` | Toggle global chat |
| `walsonchatmanager.bypass.globalmute` | Bypass global chat mute |
| `walsonchatmanager.bypass.filter` | Bypass chat filtering |
| `walsonchatmanager.filter.notify` | Receive chat-filter violation notifications |
| `walsonchatmanager.sounds` | Edit notification sound settings in the GUI |
| `walsonchatmanager.antispam` | Access anti-spam settings in the GUI |
| `walsonchatmanager.bypass.antispam` | Bypass anti-spam detection |
| `walsonchatmanager.discord.status` | View/run DiscordSRV compatibility diagnostics |

The `walsonchatmanager.admin` permission includes the child permissions listed in `plugin.yml`, including bypass permissions. Be careful when assigning it: administrators with this permission can bypass global chat mute, chat filters, and anti-spam.

Example LuckPerms commands (adjust the group name to your setup):

```text
/lp group moderator permission set walsonchatmanager.mute true
/lp group moderator permission set walsonchatmanager.mutehistory true
/lp group moderator permission set walsonchatmanager.mute.notify true
/lp group moderator permission set walsonchatmanager.filter.notify true
```

---

## Configuration files

All files are generated in:

```text
plugins/WalsonChatManager/
```

| File | Purpose |
|---|---|
| `config.yml` | Main settings, SQLite file, chat/global mute behavior, anti-spam defaults, GUI settings, and notification sounds |
| `messages.yml` | Minecraft messages and notification text |
| `chatfilter.yml` | Chat-filter settings and rules |
| `mutepresets.yml` | Mute durations, mute reasons, and unmute reasons shown in the GUI |
| `discord.yml` | DiscordSRV integration, channels, event toggles, embed styles, punishment synchronization, and diagnostics |
| `mutes.db` | SQLite database containing mute and history data and related counters |

### Main settings (`config.yml`)

Important sections include:

- `settings`: debug and message prefix.
- `database`: SQLite database type and filename.
- `chat.mute-announcements.enabled`: controls public Minecraft mute announcements.
- `chat.global-mute`: saved global mute state, staff bypass, and announcement setting.
- `chat.filtering.enabled`: master chat-filter setting.
- `anti-spam`: detection window, message threshold, cooldown, warning threshold, reset period, and automatic mute settings.
- `sounds`: per-event enabled state, sound, volume, and pitch.
- `gui`: GUI title, size, and filler item.

### Chat filter (`chatfilter.yml`)

Each rule can define:

- `enabled`
- `display-name`
- `priority`
- `match-type`
- `words`
- `actions`
- `punishment.duration`
- `punishment.reason`

The supplied configuration contains example placeholder words and an example server address. Replace these with the actual rules appropriate for your server before enabling the filter in production.

### Mute presets (`mutepresets.yml`)

Customize the duration choices and reason choices displayed in the mute/unmute GUIs. Command-based mutes can also use a duration and reason directly.

---

## DiscordSRV setup

DiscordSRV is optional. To enable the integration:

1. Install and configure DiscordSRV.
2. Invite the Discord bot to your Discord server.
3. Ensure the bot can view and send messages in the configured channels.
4. Grant `Embed Links` in channels where rich embeds are sent.
5. Grant `Moderate Members` if Discord timeout synchronization is enabled.
6. Ensure the bot's role is higher than the members it needs to timeout.
7. Enable account linking in DiscordSRV and link the Minecraft accounts that should receive synchronized punishments.
8. Configure `plugins/WalsonChatManager/discord.yml`.
9. Restart the server, then run `/wcm discordcheck` and `/wcm discordstatus`.

### Discord channel IDs

Set the channel IDs in `discord.yml`:

```yaml
channels:
  moderation: "YOUR_MODERATION_CHANNEL_ID"
  staff: "YOUR_STAFF_CHANNEL_ID"
  announcements: "YOUR_ANNOUNCEMENTS_CHANNEL_ID"
```

Use actual Discord channel IDs. Do not leave the generated all-zero placeholders in place.

### Discord embeds

Embeds can be enabled/disabled and styled in `discord.yml`:

```yaml
embeds:
  enabled: true
  timestamp: true
  footer: "WalsonChatManager"
  thumbnail-url: ""
  colors:
    announcements: "22C55E"
    staff: "F59E0B"
    moderation: "A855F7"
```

Colors are hexadecimal values without the leading `#`.

### Discord event routing

- **Announcements:** public mute/unmute and global chat state announcements.
- **Moderation:** mute/unmute records, including silent mutes.
- **Staff:** detailed mute/unmute records, global chat staff logs, and chat-filter violation logs.
- **Silent mute:** does not post to the public announcements channel, but remains visible in moderation and staff channels.

Event delivery can be individually toggled under `events` in `discord.yml`.

### Discord punishment synchronization

The `punishment-sync` section controls Minecraft mute to Discord timeout synchronization:

```yaml
punishment-sync:
  enabled: true
  mute-to-timeout: true
  guild-id: "YOUR_DISCORD_SERVER_ID"
  remove-timeout-on-unmute: true
  remove-timeout-on-unlink: true
  refresh-long-timeouts-hours: 12
```

- Only DiscordSRV-linked Minecraft accounts can be synchronized.
- Offline pending mutes do not timeout the linked Discord member until the Minecraft player joins.
- The Minecraft mute is authoritative. Discord timeouts are synchronized to its remaining duration.
- Discord's native timeout limit is 28 days. Longer and permanent mutes require periodic refreshes while the Minecraft mute remains active.
- If the bot lacks permission or its role is too low, Discord can reject the timeout. Check the console diagnostics and bot role hierarchy.

### Diagnostics

Use:

```text
/wcm discordstatus
/wcm discordcheck
```

The diagnostics report the integration's detected state. They cannot guarantee that the bot outranks every individual target; Discord validates role hierarchy when a timeout is applied.

---

## Offline mute behavior

When staff mutes a player who is offline:

1. The pending mute is saved.
2. The public Minecraft mute announcement is sent if public announcements are enabled and the mute is not silent.
3. The mute countdown does not start while the player is offline.
4. When the player joins, the mute becomes active and its duration starts.
5. The player receives the normal mute notification and configured mute sound.
6. If Discord punishment synchronization is enabled and the account is linked, the Discord timeout is applied when the player joins.

Staff can remove a pending mute with `/unmute <player>`.

---

## Anti-spam behavior

Anti-spam uses a rolling message window. With the default threshold of `5`, the sixth message inside the one-second window triggers the warning and blocks that message.

After a spam warning:

- The configured cooldown blocks chat for the cooldown duration.
- Blocked spam attempts count toward the chat violation total.
- Each separate spam warning contributes to the player's warning total.
- When the warning total reaches `warnings-before-mute` within `warning-reset-hours`, the plugin applies the configured automatic mute.
- Spam warning history is persisted so a server restart does not simply erase the warning count.

The anti-spam GUI allows authorized staff to adjust the settings. Players with `walsonchatmanager.bypass.antispam` are excluded from anti-spam detection.

---

## Database and data

- The current configuration uses SQLite (`mutes.db`).
- The plugin creates and updates its required tables/columns automatically as part of its database initialization/migration logic.
- Keep regular backups of `plugins/WalsonChatManager/mutes.db`.
- Do not delete the database when upgrading if you need to preserve active mutes, mute history, or violation records.
- The plugin's future website integration is not included in the current release. The current database is local to the Minecraft server.

---

## Troubleshooting

### Plugin does not enable

- Confirm the server is running a compatible Paper version.
- Confirm the Java runtime meets the project's Java 25 requirement.
- Check the first WalsonChatManager error in the server console; later errors may be consequences of the first failure.

### Discord messages do not appear

- Confirm DiscordSRV is installed, enabled, and connected.
- Check channel IDs in `discord.yml`.
- Give the bot `View Channel`, `Send Messages`, and `Embed Links` permissions.
- Check the relevant `events` and `settings` toggles.
- Run `/wcm discordcheck` and `/wcm discordstatus`.

### Discord timeout is rejected

- Confirm the Minecraft account is linked to Discord through DiscordSRV.
- Confirm the configured guild ID is correct.
- Give the bot `Moderate Members` permission.
- Move the bot role above the target member's roles.
- Check the console for the Discord REST response/error.

### Sound is not audible

- Check the corresponding `sounds.<event>.enabled` setting.
- Verify the sound identifier is valid for the server's Paper/Minecraft version.
- Check volume and pitch values.
- Use the sound preview in the Chat Manager GUI.

### Configuration changes do not apply

- Use `/wcm reload` for supported configuration reloads.
- Restart the server if a changed setting is not reloaded by the command.
- Check the console for YAML indentation or parsing errors.

---

## Project scope

WalsonChatManager is intended to remain a chat-management plugin. Its scope includes chat mutes, chat filtering, global chat controls, anti-spam, related GUIs/sounds, and optional DiscordSRV integration.

A general player warning system is intentionally out of scope and is planned as a separate plugin. Future website-based logs or a centralized staff dashboard are also not included in the current release.

---

## License and support
### THIS PLUGIN IS DEVELOPED BY WALSON. 
### FEEL FREE TO USE IT PROVIDED YOU GIVE ME CREDITS. 
