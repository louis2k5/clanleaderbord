# ClanSystem

Clan plugin for Paper (built against the same API baseline as the PerkSystem jar, no NMS) with roles,
private clan chat, coloured tags and two animated RGB tag perks that plug into PerkSystem.

## Build

Requires JDK 21+ and Maven.

    mvn clean package

The jar is created at `target/ClanSystem.jar`. Put it into `plugins/` next to `Perks.jar` and LuckPerms.

## Commands

| Command | Who | Permission |
|---|---|---|
| `/clan create <Name> <Tag>` | everyone without a clan | `clan.create` |
| `/clan delete <Name> <Tag>` | Leader | `clan.delete` |
| `/clan change <name\|tag> <value>` | Leader, Co-Leader | `clan.change` |
| `/clan info [Name/Tag]` | everyone | `clan.info` |
| `/clan invite <Player>` | Leader, Co-Leader, Moderator | `clan.invite` |
| `/clan accept <Name/Tag>` | invited player | `clan.accept` |
| `/clan decline <Name/Tag>` | invited player | `clan.decline` |
| `/clan promote <Player>` | Leader, Co-Leader | `clan.promote` |
| `/clan demote <Player>` | Leader, Co-Leader | `clan.demote` |
| `/clan kick <Player>` | Leader, Co-Leader | `clan.kick` |
| `/clan leave` | members (not the leader) | `clan.leave` |
| `/clan chat [msg]`, `/cc [msg]` | all roles | `clan.chat` |
| `/clan peace <Player> [end]` | members | `clan.peace` |
| `/clan color` | Leader | `clan.color` + `clan.color.<color>` |
| `/clan rgb [gradient\|breathing\|off]` | perk owners | `clan.rgb` + `perks.clan_*` |
| `/clan perkvoucher <perk> [duration] [player]` | admins | `clan.admin.voucher` |
| `/clan reload` | admins | `clan.admin.reload` |

Command permissions default to `true`; the role rules (Leader/Co-Leader/Moderator) are enforced on top of them.
Remove a permission from a LuckPerms group to switch a command off for that group.

## Tag colours (7)

`clan.color.red`, `.orange`, `.yellow`, `.green`, `.cyan`, `.blue`, `.purple` (or `clan.color.*`).
Default: false. Example:

    /lp group vip permission set clan.color.red true

## RGB perks

| Perk | Permission | Effect |
|---|---|---|
| Clan Tag Gradient | `perks.clan_gradient` | flowing rainbow gradient across the tag letters |
| Clan Tag Breathing | `perks.clan_breathing` | one colour that drifts through the spectrum while fading in and out |

* Only the player who owns and enabled the perk gets the animated tag; it replaces the clan colour for that player only.
* Toggle in the `/perks` menu (two free slots on page 1) or with `/clan rgb`. Only one can be active at a time.
* Grant with LuckPerms (`/lp user <name> permission set perks.clan_gradient true`) or with `/clan perkvoucher`.

## Peace

`/clan peace Steve` sends Steve: `<Name> wants to make peace with you! /clan peace <Name>` (clickable).
When Steve runs `/clan peace <Name>`, both players can no longer damage each other. End it with
`/clan peace <Name> end`. By default peace is limited to members of the same clan (`peace.same-clan-only`).

## Roles

Leader (gold) > Co-Leader (red) > Moderator (green) > Member (gray).
You can only kick, promote or demote players ranked below you, and only promote up to one rank below your own.
