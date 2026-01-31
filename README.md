# LGPlugin: Combat Management & Item Control
Comprehensive server utility plugin (yet another one)
The first public release brings a major overhaul to the configuration for better readability and introduces several new mechanics for granular server management, so that it can apply to the needs of server owners that are not myself.
## Features
### Combat Management
  - An efficient combat tagging system with customizable item cooldowns
  - Modify explosion damage individually from different sources
  - Drop player heads upon death
### Restrictions & Limits
  - Limit the quantity of specific items a player can carry
      - Scans Bundles, Shulker Boxes, and Ender Chests (configurable) to prevent bypassing limits
      - Group items (e.g., Breeze Rods and Wind Charges) to share a single limit cap
      - Applies configurable potion effects (Slowness, Blindness, etc.) and warning messages when a player is over-encumbered
  - Whitelist or Blacklist specific enchantments on specific item types (e.g., disable Mending on Elytras or cap armour to Protection 3)
  - Completely disable specific potion effects (like Slow Falling or Weakness) from being inflicted on players.
## Configuration
```yaml
# Very lightweight combat log
combatlog:
  enabled: true
  cooldowns: # Cooldowns to apply in combat
    TRIDENT: # Item names in this config use https: //hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html
      duration: 200 # In ticks
      global: true # True = cooldowns also applies outside of combat
    ENDER_PEARL:
      duration: 20
      global: true
    CHORUS_FRUIT:
      duration: 200
      global: true
    WIND_CHARGE:
      duration: 40
      global: true

# Whether to drop player heads on death
death-drops:
  enabled: true

# Disable specific effects from being inflicted onto *players*
disabled-potions:
  enabled: true
  effects: # https: //hub.spigotmc.org/javadocs/bukkit/org/bukkit/potion/PotionEffectType.html
    WEAKNESS: 0 # Maximum allowed level
    SLOW_FALLING: 0

# Disallow specific enchantments on specific items
restricted-enchantments:
  enabled: true
  items:
    MACE: # Item names are inclusive, ie HELMET will apply to every item with helmet in its name
      glint: false # Whether or not to show the enchantment glint
      mode: whitelist # Only allows listed enchantments, defaults to blacklist
      enchantments:
        UNBREAKING: 0 # Minimum allowed level, just set it to 0
    ELYTRA:
      glint: true
      mode: blacklist  # Disallows listed enchantments
      enchantments:
        MENDING: 0 # Maximum allowed level

# Reduce explosion damage per item
explosion-damage: # Applies before armour damage reduction calculation
  enabled: true
  bed: 0.25 # Multiplier is applied before armour, make sure to check damage in game
  anchor: 0.25
  cart: 0.4
  crystal: 0.25

# System set in place to limit the maximum amount of items players can carry
# When the item limit is exceeded, apply effects to the player
item-limits:
  enabled: true
  scan-bundles: true
  scan-shulkers: false
  scan-echest: false
  message-type: ACTION_BAR # ACTION_BAR or TITLE
  text: "You are overencumbered and cannot run!"
  subtitle: "Drop excess items to move" # Only used if message-type is TITLE
  items:
    TOTEM_OF_UNDYING: 1
    ENDER_PEARL: 32
    COBWEB: 64
    EXPERIENCE_BOTTLE: 128
    TURTLE_MASTER: 1 # Potions are added with their PotionEffectType name
  groups: # For example, you don't want players to bypass the item cap by carrying breeze rods to craft into wind charges
    wind_items: # Name groups whatever you want
      limit: 64
      items:
        WIND_CHARGE: 1 # See how awesome this is?
        BREEZE_ROD: 4
  effects: # Effects to apply to encumbered players
    BLINDNESS:
      duration: 40 # In ticks!
      amplifier: 3
    SLOWNESS:
      duration: 40
      amplifier: 3
    WEAKNESS:
      duration: 40
      amplifier: 1
```
