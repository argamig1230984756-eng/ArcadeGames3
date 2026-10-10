# ArcadeGames (Paper 1.21.10, Java 21)

Five minigames: **Prop Hunt, BlockParty, TNTRun, Party Games, Mini Walls**.

## Build
```
mvn clean package
```
Jar ends up in `target/ArcadeGames.jar` -> drop into `plugins/`.

## Player commands
- `/arcade join [arena|game]` (alias `/ag`) - e.g. `/ag join tntrun`
- `/arcade leave`, `/arcade list`
- Right-click the red bed in the waiting lobby to leave.

## Admin setup (permission `arcade.admin`)
Stand where you want the point to be, then:

| Command | What it does |
|---|---|
| `/arcade create <name> <game>` | new arena (`prophunt` `blockparty` `tntrun` `partygames` `miniwalls`) |
| `/arcade setwaiting <name>` | waiting lobby |
| `/arcade setspawn <name> [n]` | add/replace spawn n |
| `/arcade setwither <name> <1-4>` | Mini Walls wither position for team n |
| `/arcade pos1 / pos2 <name>` | region corners (stand ON the corner block) |
| `/arcade setplayers <name> <min> <max>` | player limits |
| `/arcade info / forcestart / forcestop / delete <name>` | management |

### Per game
- **TNTRun** - waiting, spawns, pos1/pos2 = bottom corner and top corner of the whole floor stack (sand/gravel over TNT works). Falling below the region eliminates you.
- **BlockParty** - waiting, spawns above the floor, pos1/pos2 = two corners of the flat floor. The floor is regenerated every round and restored afterwards.
- **Party Games** - waiting, spawns standing on the platform, pos1/pos2 = the platform's top layer. Needs a void/drop around the platform. Plays Anvil Rain, Bombardment, Sumo, Spleef in random order.
- **Prop Hunt** - waiting, spawn 1 = hiders, spawn 2 = seekers (optional). Hiders are invisible with a block-display disguise; right-click blocks with the nether star to copy them.
- **Mini Walls** - waiting, `setspawn 1..4` + `setwither 1..4` (2-4 teams). Wither dead = no more respawns for that team; last team alive wins.

Arenas are saved in `plugins/ArcadeGames/arenas.yml`.
