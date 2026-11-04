# StaffSee

Simple moderation tools for Fabric servers: warns, mutes, kicks, bans, freezes, player reports, vanish and staff chat. Everything is stored as plain text and JSON files in the world folder, no database needed.

Only the server needs the mod. Players can join with a vanilla client. Works well together with [ChestSee](https://modrinth.com/mod/chestsee-chestlogger).

## Commands

| Command | What it does |
|---------|--------------|
| `/warn <player> <reason>` | Warn a player |
| `/warns <player>` | Show a player's recent warnings |
| `/mute <player> [duration] <reason>` | Mute a player (`30m`, `2h`, `1d` or `perm`) |
| `/unmute <player>` | Unmute a player |
| `/kick <player> [reason]` | Kick a player |
| `/ban <player> [reason]` | Ban a player permanently, works for offline players too |
| `/tempban <player> <duration> [reason]` | Ban a player for `30m`, `12h`, `7d`, ... |
| `/unban <player>` | Unban a player |
| `/history <player> [page]` | All warns, mutes, kicks, freezes, bans and reports of a player |
| `/history <player> chests [page]` | The player's container activity (needs [ChestSee](https://modrinth.com/mod/chestsee-chestlogger)) |
| `/freeze <player> [reason]` | Freeze a player, e.g. for a screenshare |
| `/unfreeze <player>` | Unfreeze a player |
| `/report <player> <reason>` | Report a player (everyone can use this) |
| `/reports` | List open reports |
| `/reports resolve <id>` | Close a report |
| `/vanish` | Become invisible for normal players |
| `/sc <message>` | Write in staff chat |
| `/sc` | Toggle staff chat, so your normal messages go to staff only |

Staff commands need OP level 2, or the LuckPerms nodes below.

`/ban` and `/pardon` replace the vanilla commands (`/pardon` does the same as `/unban`) but use the same ban list, so `banned-players.json`, `/banlist` and the vanilla ban screen keep working.

## Vanish

Vanished players are removed from the tab list and aren't visible in the world for normal players. Joining and leaving while vanished doesn't show a message to them. Staff with `staffsee.vanish.see` still see vanished players, and get the join/leave messages marked with `(vanished)`. Vanish stays on after relogging and after a restart.

Vanished players are also left out of `/list`, the player count and player list in the server list, and name suggestions when typing commands.

## Logs

```
world/StaffLog/
├── _mutes.json
├── _counts.json
├── _freezes.json
├── _reports.json
├── _vanish.json
├── _chat/
│   └── 2026-07-07.txt
├── _reports/
│   └── 2026-07-07.txt
└── Steve/
    ├── warns.txt
    ├── mutes.txt
    ├── kicks.txt
    ├── bans.txt
    └── freezes.txt
```

Example from `warns.txt`:

```
[06.07.26  21:15:33]
by AdminName
Reason: Griefing spawn area
```

## Config

`config/staffsee.json` is created on the first start:

```json
{
  "autoKickAfterWarns": 0,
  "muteMessage": "You are muted.",
  "warnBroadcast": false,
  "freezeMessage": "You are frozen by staff.",
  "logChat": true,
  "reportCooldownSeconds": 60,
  "discordWebhookUrl": "",
  "discordReports": true,
  "discordPunishments": false
}
```

New options are added to the file automatically after an update.

### Discord

Create a webhook in your Discord channel (Channel settings → Integrations → Webhooks) and put its URL into `discordWebhookUrl`. Restart the server afterwards.

| Option | Meaning |
|--------|---------|
| `discordReports` | Send new player reports |
| `discordPunishments` | Send warns, mutes, kicks, freezes and bans |

Messages are sent in the background, so a slow or offline Discord never lags the server. They never ping `@everyone` or roles, even if a player writes that into a report.

## Permissions

LuckPerms is optional. StaffSee uses the [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) and falls back to OP level 2 if no permission mod is installed.

| Node | Access |
|------|--------|
| `staffsee.moderate` | all staff commands |
| `staffsee.report` | `/report` (everyone by default) |
| `staffsee.vanish` | `/vanish` |
| `staffsee.vanish.see` | see vanished players |
| `staffsee.staffchat` | read and write staff chat |

The last three default to whatever `staffsee.moderate` says, so you only need them if helpers should get less than full moderators.

```
/lp group moderator permission set staffsee.moderate true
```

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api) on your server.
2. Download StaffSee for your Minecraft version from [Modrinth](https://modrinth.com/mod/staffsee-moderation).
3. Put the jar into the `mods` folder and restart the server.

## Versions

| Minecraft | Branch |
|-----------|--------|
| 26.3 | `main` |
| 26.2 | `mc/26.2` |
| 26.1.2 | `mc/26.1.2` |
| 1.21.11 | `mc/1.21.11` |

## Building

```
./gradlew build
```

The jar ends up in `build/libs`. 26.x needs Java 25, 1.21.11 needs Java 21.

## License

MIT, see [LICENSE](LICENSE).
