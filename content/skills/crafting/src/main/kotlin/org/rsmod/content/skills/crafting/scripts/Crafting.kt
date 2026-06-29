package org.rsmod.content.skills.crafting.scripts

// ======================================================================================
// Crafting skill plugin for OpenRune 239
//
// Crafting is an artisan skill with multiple interaction types:
//
// 1. GEM CUTTING (GemCuttingEvents.kt):
//    - Use chisel on uncut gem → cut gem
//    - Opal (1), Jade (13), Red topaz (16), Sapphire (20), Emerald (27),
//      Ruby (34), Diamond (43), Dragonstone (55)
//    - Semi-precious gems (opal, jade, red topaz) can be crushed
//
// 2. POTTERY (PotteryEvents.kt):
//    - Use soft clay on pottery wheel → unfired pot/bowl
//    - Use unfired item on pottery oven → finished fired item
//    - Pot: level 1, Bowl: level 8
//
// 3. LEATHERWORKING (LeatherworkingEvents.kt):
//    - Tan cowhide at Ellis (Al Kharid) → leather/hard leather
//    - Use needle + thread + leather → leather items
//    - Gloves(1), Boots(7), Cowl(9), Vambraces(11), Body(14), Chaps(18)
//    - Hard leather body: level 28
//    - Studded body/chaps: level 41/44
//
// 4. SPINNING (SpinningEvents.kt):
//    - Use wool on spinning wheel → ball of wool
//    - Level 1, 2.5 XP per wool
//
// 5. JEWELRY MAKING (JewelryEvents.kt):
//    - Use gold bar on furnace (with mould) → gold jewelry
//    - Ring(5), Necklace(6), Amulet(8), Bracelet(7)
//    - Gemmed variants with cut gems
//    - String amulet with ball of wool
//
// ======================================================================================
