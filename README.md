# CustomRPG - Minecraft Paper Plugin

A professional Minecraft Paper plugin featuring custom weapons, skills with cooldowns, and custom mobs with special behaviors.

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
- Paper Server 1.21.1 or higher
- Maven (for building)

## Building

```bash
mvn clean package
```

The compiled plugin JAR will be in `target/CustomRPG-1.0.jar`

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
