# LGPlugin: Combat Management & Item Control
Comprehensive server utility plugin (yet another one)\
The first public release brings a major overhaul to the configuration for better readability and introduces several new mechanics for granular server management, so that it can apply to the needs of server owners that are not myself.
## Features
### Combat Management
  - An efficient combat tagging system
  - Apply item cooldowns in and outside of combat
      - Distinct cooldowns for in and out of combat
      - Distinct cooldowns for the same item type with different NBT data
  - Disable restocking in combat
      - Disable ender chests
      - Disable shulker boxes
      - Disable containers in general
  - Disable elytra in combat
  - Modify explosion damage individually from different sources
  - Drop player heads upon death (with configurable drop chances)
### Restrictions & Limits
  - Limit the quantity of specific items a player can carry
      - Scans Bundles, Shulker Boxes, and Ender Chests (configurable) to prevent bypassing limits
      - Group items (e.g., Breeze Rods and Wind Charges) to share a single limit cap
      - Applies configurable potion effects (Slowness, Blindness, etc.) and warning messages when a player is over-encumbered
      - Can take NBT values for very detailed restrictions
  - Blacklist specific enchantments on specific item types
      - e.g, disable Mending on Elytras or cap armour to Protection 3
      - Also has an option to globally apply item tags
  - Disable the crafting recipe of configured items
      - Any item with any NBT value
      - Accounts for every single method of crafting
  - Completely disable specific potion effects (like Slow Falling or Weakness) from being inflicted on players.
  - Set specific items (very configurable) players spawn with on death/first login.
### Quality of Life
  - Join the world/respawn with a set configuration of items (can take NBT data, can be inserted into specified slots)
## Configuration
```yaml
# Very lightweight combat log
combatlog:
  enabled: true
  timer: 30 # In seconds
  disable-containers: false # Disable opening containers in combat (excludes echest and shulkers)
  disable-shulkers: false
  disable-echest: false
  disable-elytra: true # Disable elytra in combat
  cooldowns: # Cooldowns to apply in combat
    #   NBT matching uses substring checks against the item's full component string
    #   For NBT data, use https://minecraft.wiki/w/Data_component_format
    #   To check exact NBT, use /data get entity @s SelectedItem to see the component structure
    #   Format: "key: value" or just "value" if unique enough
    - material: TRIDENT
      #   ^^ All materials in this config can either be in the form of Bukkit Enum names
      #   https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html
      #   or Minecraft Namespaced Keys, e.g:
      #   material: minecraft:trident
      duration: 100 # In ticks
      global: false # True = cooldowns also applies outside of combat
      nbt:
        enchantments:
          RIPTIDE: 1
      #   ^^ All enchantments in this config can either be in the form of Bukkit Enum names
      #   https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/enchantments/Enchantment.html
      #   or Minecraft Namespaced Keys, e.g:
      #   "minecraft:riptide": 1
    - material: TRIDENT
      duration: 200
      global: false
      nbt:
        enchantments:
          RIPTIDE: 2
    - material: TRIDENT
      duration: 300
      global: false
      nbt:
        enchantments:
          RIPTIDE: 3
    #   Example of other Component checks using Map format
    #   - material: STICK
    #     duration: 100
    #     global: true
    #     nbt:
    #       custom_model_data: 12345
    #       custom_name: "Magic Stick" # Simple string match for name works now
    #       # For complex components like food/tool that don't have special handling yet,
    #       # you can still use the specific key value from the component string:
    #       food: "{nutrition:4}"

    - material: ENDER_PEARL # Different cooldowns in and out of combat
      duration: 50
      global: true
    - material: ENDER_PEARL
      duration: 40
      global: true
    - material: CHORUS_FRUIT
      duration: 200
      global: false
    - material: WIND_CHARGE
      duration: 40
      global: false

# Whether to drop player heads on death
death-drops:
  enabled: true
  drop-chance: 0.20 # 0 to 1, not a percentage

# Give items to players on respawn or first join
respawn-kit:
  enabled: true
  first-login: true
  death: true
  cooldown: 300 # In seconds
  items:
    - slot: armor.head
      # ^^ Just go with https://minecraft.wiki/w/Slot
      # I mean I've added a bunch of compatibility so you can also use head or helmet or 39
      item: LEATHER_HELMET
      count: 1
      nbt:
        enchantments:
          PROTECTION: 4
          VANISHING_CURSE: 1
    - slot: armor.chest
      item: LEATHER_CHESTPLATE
      count: 1
      nbt:
        enchantments:
          PROTECTION: 4
          VANISHING_CURSE: 1
    - slot: armor.legs
      item: IRON_LEGGINGS
      count: 1
      nbt:
        enchantments:
          PROTECTION: 4
          VANISHING_CURSE: 1
    - slot: armor.feet
      item: IRON_BOOTS
      count: 1
      nbt:
        enchantments:
          PROTECTION: 4
          VANISHING_CURSE: 1
    - slot: hotbar.0
      item: IRON_PICKAXE
      count: 1
      nbt:
        enchantments:
          EFFICIENCY: 2
          VANISHING_CURSE: 1
    - slot: hotbar.8
      item: RECOVERY_COMPASS
      count: 1
      nbt:
        enchantments:
          VANISHING_CURSE: 1
    - slot: weapon.offhand
      item: COOKED_BEEF
      count: 8

# Disable specific effects from being inflicted onto *players*
disabled-potions:
  enabled: true
  effects:
    WEAKNESS: 0 # Maximum allowed level
    #   ^^ All effects in this config can either be in the form of Bukkit Enum names
    #   https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/potion/PotionEffectType.html
    #   or Minecraft Namespaced Keys, e.g:
    #   "minecraft:weakness": 0
    SLOW_FALLING: 0

# Disallow specific enchantments on specific items
restricted-enchantments:
  enabled: true
  items: # Priority goes down the list
    MACE: # Item names are inclusive, ie HELMET will apply to every item with helmet in its name
      glint: false # Whether or not to show the enchantment glint
      enchantments:
        DENSITY: 0 # Maximum allowed level
        BREACH: 0
        WIND_BURST: 0
    ELYTRA:
      glint: true
      enchantments:
        MENDING: 0
    #   HELMET:
    #     glint: true
    #     enchantments:
    #       PROTECTION: 3
    GLOBAL:
      enchantments:
        VANISHING_CURSE: 0

restricted-crafting:
  enabled: true
  items:
    - material: SPEAR # Also inclusive
    - material: TIPPED_ARROW
      nbt:
        potion_contents: "harming" # Covers both Harming I and II!

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
  message-type: TITLE # ACTION_BAR or TITLE
  text: "You are overencumbered!"
  subtitle: "Drop excess items to move"

  limits:
    - material: TOTEM_OF_UNDYING
      limit: 1
    - material: ENDER_PEARL
      limit: 32
    - material: COBWEB
      limit: 64
    - material: EXPERIENCE_BOTTLE
      limit: 128

  # Group limits (Weighted items)
  groups:
    wind_items:
      limit: 64
      items:
        - material: WIND_CHARGE
          weight: 1
        - material: BREEZE_ROD
          weight: 4
    turtle_master:
      limit: 2
      items:
    #   Sorry there isn't a very good way to intuitively implement inclusive item formatting here
        - material: POTION
          weight: 2
          nbt:
            potion_contents: "minecraft:strong_turtle_master"
        - material: POTION
          weight: 1
          nbt:
            potion_contents: "minecraft:long_turtle_master"
        - material: POTION
          weight: 1
          nbt:
            potion_contents: "minecraft:turtle_master"
        - material: SPLASH_POTION
          weight: 2
          nbt:
            potion_contents: "minecraft:strong_turtle_master"
        - material: SPLASH_POTION
          weight: 1
          nbt:
            potion_contents: "minecraft:long_turtle_master"
        - material: SPLASH_POTION
          weight: 1
          nbt:
            potion_contents: "minecraft:turtle_master"
        - material: LINGERING_POTION
          weight: 2
          nbt:
            potion_contents: "minecraft:strong_turtle_master"
        - material: LINGERING_POTION
          weight: 1
          nbt:
            potion_contents: "minecraft:long_turtle_master"
        - material: LINGERING_POTION
          weight: 1
          nbt:
            potion_contents: "minecraft:turtle_master"
  effects:
    BLINDNESS:
      duration: 21 # In ticks
      amplifier: 3
    SLOWNESS:
      duration: 21
      amplifier: 3
    WEAKNESS:
      duration: 21
      amplifier: 1
```
