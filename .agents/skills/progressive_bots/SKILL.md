---
name: progressive_bots
description: Provides the system specifications, behavior tree designs, economy guides, and implementation roadmaps for OpenRune's progressive bot ecosystem.
---

# OpenRune Progressive Bots System Guide

This skill provides full access to the design specifications, behavioral architecture, and implementation roadmaps for OpenRune's progressive player bot ecosystem. 

---

## 1. System Specifications & References

The complete architectural designs for all parts of the progressive bot framework are located in the `references/` directory of this skill:

1. **Combining Architecture & Subsystems**: Refer to [progressive_bots_combining_architecture.md](file:///root/Runescape/open_rune/OpenRune-Server/.agents/skills/progressive_bots/references/progressive_bots_combining_architecture.md) for the Mermaid flowchart mapping configs, network handlers, behavior tree decorators, and the websocket agent bridge.
2. **Questing & Complex Skills**: Refer to [bot_logic_spec_quests_and_complex_skills.md](file:///root/Runescape/open_rune/OpenRune-Server/.agents/skills/progressive_bots/references/bot_logic_spec_quests_and_complex_skills.md) for details on Dialogue Sequence Runners, Hunter state machines, Construction POH build interfaces, and Sailing tacking mechanics.
3. **Minigames & World Events**: Refer to [bot_logic_spec_minigames_events.md](file:///root/Runescape/open_rune/OpenRune-Server/.agents/skills/progressive_bots/references/bot_logic_spec_minigames_events.md) for specs on Castle Wars flags, Pest Control portals, and collaborative boss fights.
4. **House Parties**: Refer to [bot_logic_spec_house_parties.md](file:///root/Runescape/open_rune/OpenRune-Server/.agents/skills/progressive_bots/references/bot_logic_spec_house_parties.md) for boxing rings, parlor beverage loops, and dining hall socialization.

---

## 2. Guidelines for Content Creators (LLM Agents)

When implementing new quests, skills, or behaviors, you must adhere to the following principles:
* **Decouple Logic**: Write self-contained leaf nodes under `org.rsmod.content.other.progressivebots.tree`. Do not hardcode parameters inside the nodes; read them from player variables or cache managers.
* **Integrate Safety Net Decorators**: Always wrap active movement or grinding nodes inside `StuckDetectorDecorator` (to recover from collision locks) and `XPWatchdogDecorator` (to recover from depleted resource loops).
* **Respect the Event Bus**: Publish custom events (like `PlayerChatEvent`) rather than polluting network handlers with inline scripting.
* **Maintain Type Safety**: When instantiating `InvObj` manually, opt-in using `@file:OptIn(dev.openrune.types.util.UncheckedType::class)`.
