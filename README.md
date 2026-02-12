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

cooldowns: # Item cooldowns
  #   NBT matching uses substring checks against the item's full component string
  #   For NBT data, use https://minecraft.wiki/w/Data_component_format
  #   To check exact NBT, use /data get entity @s SelectedItem to see the component structure
  #   Format: "key: value" or just "value" if unique enough
  - material: "minecraft:trident"
    #   ^^ All materials in this config can either be in the form of Minecraft Namespaced Keys
    #   or Bukkit enum names (https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html), e.g:
    #   material: TRIDENT
    duration: 100 # In ticks
    global: false # True = cooldowns also applies outside of combat
    nbt:
      enchantments:
        "minecraft:riptide": 1
    #   ^^ All materials in this config can either be in the form of Minecraft Namespaced Keys
    #   or Bukkit enum names (https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/enchantments/Enchantment.html), e.g:
    #   RIPTIDE: 1
  - material: "minecraft:trident"
    duration: 200
    global: false
    nbt:
      enchantments:
        "minecraft:riptide": 2
  - material: "minecraft:trident"
    duration: 300
    global: false
    nbt:
      enchantments:
        "minecraft:riptide": 3
  #   Example of other Component checks using Map format
  #   - material: "minecraft:stick"
  #     duration: 100
  #     global: true
  #     nbt:
  #       custom_model_data: 12345
  #       custom_name: "Magic Stick" # Simple string match for name works now
  #       # For complex components like food/tool that don't have special handling yet,
  #       # you can still use the specific key value from the component string:
  #       food: "{nutrition:4}"
  - material: "minecraft:ender_pearl" # Different cooldowns in and out of combat
    duration: 50
    global: false
  - material: "minecraft:ender_pearl"
    duration: 40
    global: true
  - material: "minecraft:chorus_fruit"
    duration: 200
    global: false
  - material: "minecraft:wind_charge"
    duration: 40
    global: false
  - material: "minecraft:totem_of_undying"
    duration: 600
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
      item: "minecraft:leather_helmet"
      count: 1
      nbt:
        enchantments:
          PROTECTION: 4
          "minecraft:vanishing_curse": 1
    - slot: armor.chest
      item: "minecraft:leather_chestplate"
      count: 1
      nbt:
        enchantments:
          "minecraft:protection": 4
          "minecraft:vanishing_curse": 1
    - slot: armor.legs
      item: "minecraft:iron_leggings"
      count: 1
      nbt:
        enchantments:
          "minecraft:protection": 4
          "minecraft:vanishing_curse": 1
    - slot: armor.feet
      item: "minecraft:iron_boots"
      count: 1
      nbt:
        enchantments:
          "minecraft:protection": 4
          "minecraft:vanishing_curse": 1
    - slot: hotbar.0
      item: "minecraft:iron_pickaxe"
      count: 1
      nbt:
        enchantments:
          "minecraft:efficiency": 2
          "minecraft:vanishing_curse": 1
    - slot: hotbar.8
      item: "minecraft:recovery_compass"
      count: 1
      nbt:
        enchantments:
          "minecraft:vanishing_curse": 1
    - slot: weapon.offhand
      item: "minecraft:cooked_beef"
      count: 8

# Disable specific effects from being inflicted onto *players*
disabled-potions:
  enabled: true
  effects:
    "minecraft:weakness": -1 # Maximum allowed level
    #   ^^ All effects in this config can either be in the form of Minecraft Namespaced Keys
    #   or Bukkit Enum names (https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/potion/PotionEffectType.html), e.g:
    #   WEAKNESS: -1
    "minecraft:slow_falling": -1

# Disallow specific enchantments on specific items
restricted-enchantments:
  enabled: true
  items: # Priority goes down the list
    "minecraft:mace": # Item names are inclusive, ie HELMET will apply to every item with helmet in its name
      glint: false # Whether or not to show the enchantment glint
      enchantments:
        "minecraft:density": 0 # Maximum allowed level
        "minecraft:breach": 0
        "minecraft:wind_burst": 0
    "minecraft:elytra":
      glint: true
      enchantments:
        "minecraft:mending": 0
    #   HELMET:
    #     glint: true
    #     enchantments:
    #       "minecraft:protection": 3
    GLOBAL:
      enchantments:
        "minecraft:vanishing_curse": 0

restricted-crafting:
  enabled: true
  items:
    - material: "spear" # Also inclusive
    - material: "minecraft:tipped_arrow"
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
    - material: "minecraft:totem_of_undying"
      limit: 1
    - material: "minecraft:ender_pearl"
      limit: 32
    - material: "minecraft:cobweb"
      limit: 64
    - material: "minecraft:experience_bottle"
      limit: 128

  # Group limits (Weighted items)
  groups:
    wind_items:
      limit: 64
      items:
        - material: "minecraft:wind_charge"
          weight: 1
        - material: "minecraft:breeze_rod"
          weight: 4
    turtle_master:
      limit: 2
      items:
        - material: "minecraft:potion"
          weight: 2
          nbt:
            potion_contents: "minecraft:strong_turtle_master"
        - material: "minecraft:potion"
          weight: 1
          nbt:
            potion_contents: "minecraft:long_turtle_master"
        - material: "minecraft:potion"
          weight: 1
          nbt:
            potion_contents: "minecraft:turtle_master"
  effects:
    "minecraft:blindness":
      duration: 39 # In ticks
      amplifier: 3
    "minecraft:slowness":
      duration: 39
      amplifier: 3
```
