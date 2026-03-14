[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
# GriefHeal 🛠️

GriefHeal is a high-performance, open-source world restoration engine for Minecraft 1.21.1. 

### Why it was created
This plugin was built to replace outdated solutions like **CreeperHeal** and **CreeperHeal2**, which often struggle with modern block states and performance requirements. It also adds critical functionality missing from **EnhancedCreeperHeal**, specifically advanced blacklisting, container item restoration, and anti-dupe measures.

### What it does
- **Automatic Restoration:** Automatically repairs damage caused by explosions (TNT, Creepers, Ghasts, etc.) and fire.
- **SQLite Persistence:** All pending repairs are saved to a local database. If the server crashes, repairs are automatically recovered on boot.
- **Spatial Debouncing:** Intelligently waits for "chain reactions" to finish; the timer resets until the specific area goes quiet.
- **Layered Outside-In Healing:** Craters heal from the outside edges inward. Solids are prioritized, and fragile blocks (Redstone, Rails, Torches) are placed last to prevent physics "popping."
- **Advanced Blacklisting:** Prevents specific blocks (like TNT or Beacons) from ever regenerating, stopping infinite explosion loops.
- **Anti-Dupe:** Sets explosion item yields to 0% so players cannot exploit restoration for infinite resources.
- **Container Memory:** Optionally restores chests and other containers with their original contents.
- **Redstone Preservation:** Correctly restores redstone orientations and repeater timings.
- **Aesthetic Restoration:** Integrated pitched-down Beacon pulses and Reverse Portal particles for a "time-reversal" effect.
- **Performance Optimized:** Uses a gradual "blocks-per-tick" restoration system to prevent server lag on large nukes.

## 🚀 Installation
1. Download the latest `GriefHeal.jar` from the [Releases](https://github.com/adamwbb/GriefHeal/releases) page.
2. Drop the JAR into your server's `plugins` folder.
3. Restart your server to generate the default configuration and `griefheal.db`.
4. Configure your restoration delay and world blacklist in `plugins/GriefHeal/config.yml`.
5. Run `/gh reload` to apply any changes.

## 🛠️ Commands & Permissions
| Command | Alias | Description | Permission |
| :--- | :--- | :--- | :--- |
| `/gh reload` | `/griefheal reload` | Reloads the configuration file. | `griefheal.admin` |
| `/gh now` | `/griefheal now` | Instantly restores all pending blocks in the queue. | `griefheal.admin` |

## ⚙️ Configuration (config.yml)
```yaml
# GriefHeal Config

# Delay in seconds before restoration starts (Default is 6 minutes)
restoration-delay: 360

# How many blocks to restore per tick (1 tick = 0.05s)
blocks-per-tick: 10

# Spatial Debounce: Max distance to group explosions as one "event"
spatial-debounce-radius: 50.0

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
explosions:
  creeper: true
  primed_tnt: true
  fireball: true
  wither: true
  wither_skull: true
  end_crystal: true
  tnt_minecart: true
  block_explosion: true

# Blocks that will NEVER be restored.
blacklist:
  - TNT
  - END_CRYSTAL
  - TNT_MINECART
  - BEACON
  - BEDROCK
  - RESPAWN_ANCHOR

# Visuals and Audio
effects:
  sounds-enabled: true
  particles-enabled: true
