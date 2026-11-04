# Changelog

## 1.2

- `/ban`, `/tempban`, `/unban` with logging, using the vanilla ban list
- vanilla `/pardon` is logged as an unban too
- `/history <player>` with all punishments and reports of a player
- `/history <player> chests` with the ChestSee logs of a player (if ChestSee is installed)
- Discord webhook for reports and punishments
- `/vanish` and staff chat (`/sc`), vanished players are hidden in `/list`, the server list and name suggestions
- New permission nodes `staffsee.vanish`, `staffsee.vanish.see`, `staffsee.staffchat`
- New config options: `discordWebhookUrl`, `discordReports`, `discordPunishments`

## 1.1

- `/freeze` and `/unfreeze` to lock players in place, e.g. for screenshares
- Player reports: `/report`, `/reports`, `/reports resolve`
- Chat log in `world/StaffLog/_chat/YYYY-MM-DD.txt`
- LuckPerms support with `staffsee.moderate` and `staffsee.report` (falls back to OP level 2)
- New config options: `freezeMessage`, `logChat`, `reportCooldownSeconds`

## 1.0

- `/warn`, `/warns`, `/mute`, `/unmute`, `/kick`
- Per-player log files in `world/StaffLog`
