# StaffSee

Simple moderation tools for Fabric servers: warns, mutes, kicks, freezes and player reports. Everything is stored as plain text and JSON files in the world folder, no database needed.

Only the server needs the mod. Players can join with a vanilla client. Works well together with [ChestSee](https://modrinth.com/mod/chestsee-chestlogger).

## Commands

| Command | What it does |
|---------|--------------|
| `/warn <player> <reason>` | Warn a player |
| `/warns <player>` | Show a player's recent warnings |
| `/mute <player> [duration] <reason>` | Mute a player (`30m`, `2h`, `1d` or `perm`) |
| `/unmute <player>` | Unmute a player |
| `/kick <player> <reason>` | Kick a player |
| `/freeze <player> [reason]` | Freeze a player, e.g. for a screenshare |
| `/unfreeze <player>` | Unfreeze a player |
| `/report <player> <reason>` | Report a player (everyone can use this) |
| `/reports` | List open reports |
| `/reports resolve <id>` | Close a report |

Staff commands need OP level 2, or the LuckPerms nodes below.

## Logs

```
world/StaffLog/
├── _mutes.json
├── _counts.json
├── _freezes.json
├── _reports.json
├── _chat/
│   └── 2026-07-07.txt
├── _reports/
│   └── 2026-07-07.txt
└── Steve/
    ├── warns.txt
    ├── mutes.txt
    ├── kicks.txt
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
  "reportCooldownSeconds": 60
}
```

## Permissions

LuckPerms is optional. StaffSee uses the [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) and falls back to OP level 2 if no permission mod is installed.

| Node | Access |
|------|--------|
| `staffsee.moderate` | all staff commands |
| `staffsee.report` | `/report` (everyone by default) |

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
| 26.2 | `main` |
| 26.1.2 | `mc/26.1.2` |
| 1.21.11 | `mc/1.21.11` |

## Building

```
./gradlew build
```

The jar ends up in `build/libs`. 26.x needs Java 25, 1.21.11 needs Java 21.

## License

MIT, see [LICENSE](LICENSE).
