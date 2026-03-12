# GriefHeal

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

### Commands
- `/gh reload` - Reloads the configuration.
- `/gh healnow` - Instantly restores all pending blocks in the queue.
