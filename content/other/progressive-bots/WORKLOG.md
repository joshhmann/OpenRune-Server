# Progressive Bots Worklog

## Phase 1 & 2: Core Architecture & Dummy Trees
- Developed `BotManager.kt` plugin to spawn and tick bots via the `GameLifecycle.LateCycle` event.
- Built a Behavior Tree engine (`BotBehaviorTree.kt` with `SequenceNode`, `SelectorNode`, `InvertNode`).
- Constructed an archetype-based Utility system (`BotPersonality.kt`) to determine high-level goals.
- Added data classes for trajectories (`TrajectoryCapture.kt`) to export bot paths for external LLM analytics.
- Stubbed out spatial memory (`SpatialMemory.kt`) for future heatmap avoidance.

## Phase 3: Robustness & Safety Nets (Lost-City Parity)
- Implemented `ClaimRegistry.kt` to prevent bots from dogpiling on the same NPC or resource.
- Created `StuckDetectorDecorator` to measure if a bot is oscillating or blocked, aborting goals to trigger reassessment.
- Created `XPWatchdogDecorator` to monitor stat xp; if a bot goes 150 ticks without a drop, it forces a goal swap.
- Created `GateSweeperDecorator` to scan 5-tile radius for closed gates and force interaction.
- Refactored `BotPersonality.kt` to wrap all generated goals in the decorators.

## Phase 4: Real Engine Integration
- Updated `ProgressionRegistry.kt` with `TargetUpgrade` mapping so bots know which trees/rocks to target at specific levels.
- Created `GatherTree.kt` to replace the dummy skilling nodes with a full Selector/Sequence tree:
  - Added `HasToolNode` which verifies the tool requirement against the player's inventory.
  - Added `FindTargetNode` which searches the `LocRegistry` 3x3 surrounding zones for the target (e.g. `oak_tree`), verifying it against the `ClaimRegistry` before setting the destination.
  - Added `InteractTargetNode` which engages OpenRune's core pathfinder and event system by injecting a `RouteRequestLoc` and `InteractionLocOp` into the player.

## Phase 5: Combat, Social Chat Systems, and Advanced Economy (Endgame)
- Fully implemented `FightTree.kt` using `NpcList` scanning, `ProgressionRegistry` weapon thresholds, and native OpenRune NPC interaction pathfinding (`RouteRequestPathingEntity`, `InteractionNpcOp`).
- Added `PlayerChatEvent.kt` to the player core APIs to allow scripts/plugins to listen to incoming public chat.
- Modified `MessagePublicHandler.kt` in the network layer to publish `PlayerChatEvent` whenever a player sends a chat message.
- Implemented `ChatResponseSystem.kt` inside the bots module to parse and match intents from `chat_responses.json`. It dynamically injects `{name}`, `{myname}`, skill levels, and `{total}` level into the responses.
- Added listener inside `BotManager.kt` that detects player chat, checks for nearby bots (within 8 tiles), and makes them reply using the response system.
- Upgraded `BotConfig.kt` to automatically load the bot definitions and spawnpoints from an external `progressive_bots.yml` configuration, with full Jackson YAML mapping, falling back to the default list on missing files.
- Created `EconomyManager.kt` providing actual buy/sell store dynamics, allowing bots to earn GP from skilling drops and spend GP to upgrade axes/pickaxes/swords.
- Integrated `ProductionTree.kt` allowing multi-step skilling (mining copper & tin ores -> pathing to furnace to smelt bronze bars -> pathing to anvil to smith bronze daggers -> selling to shops via `EconomyManager`).
- Updated `ChatResponseSystem.kt` with an asynchronous `HttpClient` query that calls a local LLM server (like Ollama or a Python sidecar) to generate dynamic, personality-driven, contextual chat replies, with automatic zero-delay fallback to local pattern matching if the LLM is offline or times out.
