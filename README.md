# CustomRPG - Minecraft Paper Plugin

> **2026-10-24 Chicken motion repair:** Egg trails now use actual world positions and current projectile velocity; healer and fighting chickens can descend cliffs and hop one-block steps while retaining collision and unloaded-chunk checks. See [verification and deployment](docs/CHICKEN_RUNTIME.md). Live Paper visuals still need acceptance. **822 tests pass; the backed-up JAR is staged in plugins/update for the next full restart.**

> **2026-10-24 Chicken tuning:** Chicken damage now scales with total Spirit; baby and stampede participation/bonus caps are removed. Supply summons two real adult healers for ten seconds; Chicken God stays thirty seconds. Nest places a real temporary composter with protected placement and a restoration journal. Growth and heart visuals use supported Java particles. See [current mechanics and deployment](docs/CHICKEN_RUNTIME.md); earlier Chicken notes below describe the first version. **814 tests pass; the backed-up live server JAR was updated while the server was offline. Full startup required.**

> **2026-10-24 Chicken Master:** Added an independent seventeenth branch, seven normals plus Chicken God, real egg hatching, supplies, projectile interception, a controlled fighting rooster and a genuinely scaled Chicken ultimate. Current catalog: **220 talents / 43 VFX DNA definitions / 240 inventory entries**. Egg mechanism; equip five normals on 1–4 / F, shared ultimate on Shift+F. See [mechanics, verification and deployment](docs/CHICKEN_RUNTIME.md). Live Paper behavior, visuals and multiplayer performance still require acceptance; earlier counts below are historical. **Final validation: 793 tests pass; the backed-up offline server JAR is installed for the next full startup.**

> **2026-10-24 fang update:** Spinal Lance now displays native EvokerFangs in combat and harmless previews; existing damage, aim, range, MP and cooldown remain intact. Temporary jaws have native-damage guards, visibility filtering, caps and cleanup. **754 tests pass**. The server stopped before deployment; the backed-up live JAR is updated for the next full startup. See [details](docs/NECROMANCER_RUNTIME.md).

> Necromancer now adds an independent branch with seven normal abilities, the Gate of the Dead ultimate, a two-slot controlled undead roster, sacrifice and Colossus fusion. Current catalog: **16 branches / 212 talents**, with **35 independent VFX DNA definitions**. Mechanism item: **bone**; equip five normal skills on 1–4 / F, ultimate on Shift+F. See [Necromancer mechanics and acceptance](docs/NECROMANCER_RUNTIME.md). Live Paper combat, visuals and multiplayer performance remain unverified; older counts below are historical. Final `mvn verify`: **745 tests pass**, JAR backed up and installed in the offline server plugins directory; full startup required.

A professional Minecraft Paper plugin featuring custom weapons, skills with cooldowns, and custom mobs with special behaviors.

## Current Project Status

Dark now implements Eternal Night: seven normal abilities, Eclipse ultimate, innate soul brands, actual-damage Echo recording, a particle-only claw servant and eight separate previews. The original twelve Dark actives resolve to eight new abilities while raw ranks, points and saved bindings remain preserved. Current total: **15 branches / 204 talents**. Dark uses wither roses; Frost sticks; Earth flint. See [Dark mechanics, compatibility and acceptance](docs/DARK_RUNTIME.md). Latest complete build: **717 tests pass**. Live combat, visuals and server performance remain unverified; earlier counts below are historical.

Earth Guardian now implements Mountain Oath: seven normal skills, Titan's Dominion, innate resilience, party damage sharing, directional defense and eight independent VFX previews. The 15 branches now contain **208 talents**. Equip five normals and one shared ultimate (Frost Crown or Titan) on Shift+F. Earth uses flint; Frost uses sticks. See [Earth mechanics and acceptance notes](docs/EARTH_RUNTIME.md). Latest complete build: **683 tests pass**. Live Paper combat, visuals and multiplayer performance remain unverified. Earlier baseline counts below are historical.

Frost now has seven learnable normal skills (equip five on 1–4 / F), Zero-Degree Sovereignty on Shift+F, innate frost marks, eight distinct particle animations and harmless `/vfx` previews. Old Frost IDs and learned ranks are preserved. The original 15 branches now contain 204 talents; the eight-theme menu and 80 core talents remain removed. See [Frost controls, mechanics and acceptance notes](docs/FROST_RUNTIME.md). Latest `mvn clean verify`: **651 tests pass**; live Paper combat, visuals and multiplayer performance need verification.

Player-owned burn ticks carry the caster in a native ON_FIRE DamageSource, retaining combat budgets and native kill attribution. All 204 talent tooltips and binding previews show only introduction, trigger, cooldown, cost, mechanism item and damage formula. See [damage and tooltip notes](docs/TALENT_DAMAGE_AND_LORE.md).

The VFX engine offers eight Frost previews plus the original three independent samples. W01 remains a preview sample only. See [the runtime guide](docs/VFX_RUNTIME.md). Headless 32/64-animation budget checks are recorded in the Frost guide; they do not measure server TPS or client FPS.

Custom weapons hide their level requirement once the holder reaches the required RPG level, and restore it for a lower-level recipient. Inventory and level changes refresh the display; see [runtime notes](docs/BALANCE_RUNTIME.md).

The [Lv.1–60 PvE balance model](docs/INITIAL_BALANCE_MODEL.md) is integrated into combat v2, with backed-up player migration. See [deployment and verification](docs/BALANCE_RUNTIME.md); Paper gameplay testing is still pending.

See [the feature inventory and implementation progress](docs/PROJECT_STATUS.md) for the current MMORPG systems, vanilla-world spawning rules, configuration migration, and verification checklist. The original feature overview below is not a complete inventory.

## Features

### Custom Weapons
- **Iron Scythe (鐮刀)**: Deals extra damage when attacking from behind
- **Fire Sword**: Sets enemies on fire with each hit
- **Thunder Axe**: 30% chance to strike lightning on hit

### Skills with Cooldowns
- **Fireball** (Blaze Rod + Right Click): Launch a fireball projectile, 10s cooldown
- **Heal** (Golden Apple + Left Click): Restore 5 hearts, 20s cooldown
- **Dash** (Feather + Right Click): Quick forward dash, 5s cooldown

### Custom Mobs
- **Snow Zombie**: Zombie that throws snowballs at players
- **Fire Skeleton**: Skeleton that shoots fire arrows
- **Giant Slime**: Extra large slime that splits into smaller slimes on death

## Requirements

- Java 21
- Built against Paper API 1.21.11; use a compatible Paper 1.21.11 test server. Other 1.21.x versions have not been verified.
- Maven (for building)

## Building

```bash
mvn clean package
```

The compiled plugin JAR will be in `target/CustomRPG-1.0.jar`

Run `mvn clean verify` to build and execute the regression suite. See [the recovery and audit handoff](docs/AI_AGENT_HANDOFF.md) for verified fixes, remaining risks, and the Paper server acceptance checklist.

Dynamic world events include Meteor Impact on a temporary island above existing terrain, one-time mobs, personal material wallets, and restart cleanup. Use `/events join` to enter and `/events leave` to return. Read [the world event guide](docs/WORLD_EVENTS.md) for commands, configuration, recovery and test-server acceptance requirements.

The race roster now has 12 races. Read [the race guide and balance review](docs/RACES.md) for new race IDs, safe configuration upgrades, working passives, persistence fixes, and the existing seven-race assessment.

The vanilla-client [mob encounter guide](docs/MOB_ENCOUNTERS.md) lists 153 appearance/ability profiles, 20 new biome mobs, 10 rare wilderness bosses, upgrade settings, and `/custommob spawn <id> [level]` testing commands.

There are now 15 talent trees and 200 talents, including a [Hunter-inspired Nen tree](docs/HUNTER_TALENTS.md) with 36 talents grouped into Gon, Killua, Kurapika, Hisoka, Netero and common training pages. Character effects include charged fists, lightning afterimages, chains, cards and a golden multi-arm aura. Use `/talent hunter`. The [talent expansion guide](docs/TALENT_EXPANSION.md) covers 24 new frost, storm, earth, and light talents, hotkeys, party support, and safe configuration upgrades.

The [crafting and village merchant guide](docs/CRAFTING_AND_MERCHANTS.md) adds 980 equipment items, 84 mob materials, native crafting-table recipes, and persistent village equipment traders selling gear for emeralds at local region levels. Use `/rpgcraft` for the in-game guide. Three grades of [health and mana potions](docs/POTIONS.md) can also be crafted from mob materials; browse `/rpgcraft potions`.

## Installation

1. Build the plugin using Maven
2. Copy the JAR file to your Paper server's `plugins` folder
3. Start or restart your server
4. The plugin will generate a `config.yml` file in `plugins/CustomRPG/`

## Commands

- `/weapon <weapon_name> [player]` - Give a custom weapon
- `/weapon list` - List all available weapons

### Examples
```
/weapon iron_scythe
/weapon fire_sword PlayerName
/weapon list
```

## Permissions

- `customrpg.*` - All permissions
- `customrpg.weapon` - Use weapon command for yourself
- `customrpg.weapon.others` - Give weapons to other players
- `customrpg.admin` - Administrative access

## Configuration

The `config.yml` file contains all weapon, skill, and mob configurations. You can customize:

- Weapon damage multipliers and special effects
- Skill cooldowns and effects
- Mob health, damage, and behaviors

## Project Structure

```
src/main/java/com/customrpg/
├── CustomRPG.java              # Main plugin class
├── commands/
│   └── WeaponCommand.java      # /weapon command handler
├── managers/
│   ├── WeaponManager.java      # Weapon creation and management
│   ├── SkillManager.java       # Skill activation and cooldowns
│   └── MobManager.java         # Custom mob spawning
├── listeners/
│   ├── WeaponListener.java     # Weapon special effects
│   ├── SkillListener.java      # Skill activation events
│   └── MobListener.java        # Custom mob behaviors
└── utils/
    └── CooldownUtil.java       # Cooldown management utility

src/main/resources/
├── plugin.yml                  # Plugin metadata
└── config.yml                  # Plugin configuration
```

## Development

This plugin uses a modular architecture with clear separation of concerns:

- **Managers**: Handle data loading and business logic
- **Listeners**: Process Bukkit events
- **Commands**: Handle player commands
- **Utils**: Provide utility functions

All classes are well-documented with JavaDoc comments explaining their purpose and usage.

## License

This project is provided as a template for Minecraft plugin development.
