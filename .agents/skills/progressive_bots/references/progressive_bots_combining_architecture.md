# OpenRune Progressive Bots: System Architecture & Integration Summary

This document provides a birds-eye view of how the Progressive Bots system, the Economy Engine, the Proximity Social Chat system, the QA Override system, and the external AgentBridge connect together to form a living, interactive server ecosystem.

---

## 1. System Architecture Diagram

The diagram below illustrates the relationship between the server modules, the network layer, our behavior engine, and the external AI client layers:

```mermaid
graph TD
    %% Configs
    subgraph Configurations
        A1[bots.yml / game.yml]
        A2[progressive_bots.yml]
    end

    %% Network & Core API Layer
    subgraph Network & Core API
        B1[MessagePublicHandler] -->|Publishes| B2[PlayerChatEvent]
        B3[Player / NPC Entity Lists]
        B4[LocRegistry / Cache Database]
    end

    %% Bot Engine Plugin
    subgraph Progressive Bots Plugin
        C1[BotManager] -->|1. Load Configs| A2
        C1 -->|2. LateCycle Tick| C2{Evaluation}
        C2 -->|Normal Mode| C3[BotPersonality - Utility]
        C2 -->|QA Override Mode| C4[BotQaSystem Task]
        
        C3 -->|Select BT Goal| C5[Behavior Tree GoalStack]
        
        %% BT Goal Nodes
        subgraph Behavior Trees
            C5 --> D1[GatherTree: Woodcut/Mine]
            C5 --> D2[FightTree: Combat Progression]
            C5 --> D3[ProductionTree: Multi-step Smithing]
            C5 --> D4[BeggarTree: Spam & Wander Hubs]
        end
        
        %% Systems
        D1 & D2 & D3 & D4 -->|Interact| B3 & B4
        D1 & D2 & D3 & D4 -->|Check & Deduct GP| E1[EconomyManager]
        
        %% Decorators
        F1[StuckDetectorDecorator] -.->|Safeguard| C5
        F2[XPWatchdogDecorator] -.->|Safeguard| C5
        F3[GateSweeperDecorator] -.->|Safeguard| C5
    end

    %% Chat Engine
    subgraph Social Chat Engine
        B2 -->|Proximity check <= 8 tiles| G1[ChatResponseSystem]
        G1 -->|10s Player Cooldown| G2{Has Cooldown?}
        G2 -->|Yes| G3[Ignore]
        G2 -->|No: Async POST| G4[queryLLM: Ollama/OpenAI API]
        G4 -->|Failure Fallback| G5[respondWithPattern: JSON rules]
        G4 & G5 -->|Set bot.publicMessage| B3
    end

    %% External Controls
    subgraph AgentBridge
        H1[AgentBridgeServer: WebSocket 43595] <-->|Bidirectional Stream| H2[Python BotClient]
        H2 -->|Manage & Test| B3
    end

    %% Styles
    classDef config fill:#f9f,stroke:#333,stroke-width:2px;
    classDef system fill:#85C1E9,stroke:#333,stroke-width:2px;
    classDef chat fill:#D5F5E3,stroke:#333,stroke-width:2px;
    classDef network fill:#FADBD8,stroke:#333,stroke-width:2px;
    
    class A1,A2 config;
    class C1,C2,C3,C4,C5,D1,D2,D3,D4,E1,F1,F2,F3 system;
    class B1,B2,B3,B4 network;
    class G1,G2,G3,G4,G5 chat;
```

---

## 2. Subsystem Integrations

### 2.1 Game-AI & Economy Integration
* **Skilling Flow**: Bots grind resources and walk to regional trade hubs (GE, Lumbridge/Varrock general stores).
* **GP Conversions**: `EconomyManager` converts logs/ores/bars into GP based on standard cache values, allowing bots to buy tools or weapons required for the next level tier.
* **Low GP Fallbacks**: If the bot does not have enough GP to purchase upgrades, they enter a temporary beggar/scammer state to ask for coins, or switch to combat goals to farm GP drops from goblins.

### 2.2 Proximity Chat & LLM Loop
* **Interactive Spawning**: When human players enter active hubs, bots converse naturally.
* **Non-blocking Request Handling**: The main tick thread is protected. WebSocket/HTTP queries to LLM servers run in background threads, falling back instantly to pattern-matching if the endpoint times out.

### 3. QA Testing Pipeline
* **Command Override**: Developers can execute `::botqa <bot> <task>` to take control of any bot.
* **External Websocket Controls**: Through the `AgentBridge`, external agents (like Python scripts or Hermes) can bypass default behaviors, tele-jump, spawn test items, perform actions, and monitor tick-by-tick state changes to assertions.
