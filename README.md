[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
# GriefHeal 🛠️

GriefHeal is a high-performance world restoration plugin for Minecraft 1.21.1.

### Why it was created
This plugin was built to replace outdated solutions like **CreeperHeal** and **CreeperHeal2**, which often struggle with modern block states and performance requirements. It also adds critical functionality missing from **EnhancedCreeperHeal**, specifically advanced blacklisting, container item restoration, and anti-dupe measures.

### What it does
- **Automatic Restoration:** Automatically repairs damage caused by explosions (TNT, Creepers, Ghasts, etc.) and fire.
- **Advanced Blacklisting:** Prevents specific blocks (like TNT or Beacons) from ever regenerating, stopping infinite explosion loops.
- **Anti-Dupe:** Sets explosion item yields to 0% so players cannot exploit restoration for infinite resources.
- **Container Memory:** Optionally restores chests and other containers with their original contents.
- **Redstone Preservation:** Correctly restores redstone orientations and repeater timings.
- **Performance Optimized:** Uses a gradual "blocks-per-tick" restoration system to prevent server lag on large nukes.

## 🚀 Installation
1. Download the latest `GriefHeal.jar` from the [Releases](https://github.com/adamwbb/GriefHeal/releases) page.
2. Drop the JAR into your server's `plugins` folder.
3. Restart your server to generate the default configuration.
4. Configure your restoration delay and world blacklist in `plugins/GriefHeal/config.yml`.
5. Run `/gh reload` to apply any changes.

## 🛠️ Commands & Permissions
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/gh reload` | Reloads the configuration file. | `griefheal.admin` |
| `/gh healnow` | Instantly restores all pending blocks in the queue. | `griefheal.admin` |

## ⚙️ Configuration (config.yml)
```yaml
# GriefHeal Config

# Delay in seconds before restoration starts (Default is 6 minutes)
restoration-delay: 360

# How many blocks to restore per tick (1 tick = 0.05s)
# Higher = Faster healing but more CPU usage
blocks-per-tick: 10

features:
  # Should fire damage (burned blocks) heal?
  heal-fire-damage: true
  # Should chests/barrels/shulkers come back with their loot?
  restore-chest-items: true

# Worlds where GriefHeal will do NOTHING
world-blacklist:
  - creative_world
  - hub_world

# Which explosion types should be healed?
# Set to false to make them destructive forever.
explosions:
  creeper: true
  primed_tnt: true
  fireball: true          # Ghasts
  wither: true
  wither_skull: true
  end_crystal: true
  tnt_minecart: true
  block_explosion: true   # Beds and Respawn Anchors

# Blocks that will NEVER be restored.
# If these are destroyed in an explosion, they stay gone.
blacklist:
  - TNT
  - END_CRYSTAL
  - TNT_MINECART
  - BEACON
  - BEDROCK
  - RESPAWN_ANCHOR
