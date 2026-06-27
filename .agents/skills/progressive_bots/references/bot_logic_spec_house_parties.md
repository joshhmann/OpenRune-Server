# Progressive Bot System Spec: House Parties (POH Socialization)

This document details the architectural design and behavior trees required to support bot participation in Player-Owned House (POH) parties, boxing ring events, dungeon combat, and house chattering.

---

## 1. Entering House Parties

House parties require bots to coordinate joining a specific host's house instance via the Rimmington house portal.

### 1.1 Host Detection & Portal Interaction
1. **Noticeboard Scanning**: Bots path to the Rimmington portal noticeboard. They interact with the board to open the "Active House Parties" interface.
2. **Select Host**: Intercept interface data to parse the list of active hosts. Select a host with > 5 active guests to ensure a populated party.
3. **Portal Entry**: Walk to the portal, choose the "Go to friend's house" option, input the host's username via the simulated dialogue runner, and enter.

---

## 2. POH Activities Behavior Tree

Once inside a house instance, the bot's behavior is driven by a utility-weighted selector tree based on nearby POH features.

```
       [House Party Selector]
                 │
      ┌──────────┼──────────┐
      ▼          ▼          ▼
[Combat Ring] [Social Parlor] [Dining Room]
```

### 2.1 Boxing & Combat Rings (`CombatRingNode`)
* **Area Check**: Scan the region for combat ring boundary lines (`LocRegistry` matching combat boundary locs).
* **Challenge**: Walk inside the ring. Scan for other players/bots inside the ring.
* **Fight Loop**: Engage in PvP combat (Melee/Boxing rules) using standard combat loops. Step out of the ring or stop attacking when Hitpoints drop below 10% to prevent death.

### 2.2 Social Parlor & Drink Dispensing (`ParlorNode`)
* **Beverage Intake**: Path to beer barrels, space larders, or wine racks. Interact to fetch drinks (e.g. Asgardean Ale, Cider).
* **Drinking**: Consume drinks from inventory, triggering animation emotes (cheering/dancing).
* **House Chattering**: Broadcast social public chat containing party-themed phrases (e.g. "nice house!", "@@@@@ PARTY @@@@@", "who is the host?").

### 2.3 Dining Room Socials (`DiningTableNode`)
* **Sitting down**: Scan for dining chairs. Interact to sit down.
* **Chat Loop**: Engage in proximity-based chattering with other players sitting at the table.
* **Serve Sequence**: Interact with the dining table to eat spawned food items.
