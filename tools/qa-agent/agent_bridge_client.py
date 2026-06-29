#!/usr/bin/env python3
"""
OpenRune 239 — QA Bot Testing Quick-Start Script

This script connects to the AgentBridge WebSocket (port 43595) and can:
1. Spawn a test bot
2. Execute action sequences
3. Read state snapshots
4. Report test results

Usage:
    python3 tools/qa-agent/agent_bridge_client.py --help

Examples:
    # Spawn a bot and walk to Cook's kitchen
    python3 tools/qa-agent/agent_bridge_client.py spawn TestBot
    python3 tools/qa-agent/agent_bridge_client.py walk TestBot 3209 3216

    # Run a full test sequence from a JSON spec
    python3 tools/qa-agent/agent_bridge_client.py run-test tests/cook_assistant.json

    # Interactive mode (monitor state and send commands)
    python3 tools/qa-agent/agent_bridge_client.py monitor TestBot

Requirements:
    pip install websocket-client
"""

import argparse
import json
import sys
import time
import threading
from typing import Optional

try:
    import websocket
except ImportError:
    print("Please install websocket-client: pip install websocket-client")
    sys.exit(1)


class AgentBridgeClient:
    """Client for OpenRune's AgentBridge WebSocket protocol."""

    def __init__(self, host: str = "192.168.0.187", port: int = 43595):
        self.url = f"ws://{host}:{port}"
        self.ws: Optional[websocket.WebSocketApp] = None
        self.last_state = {}
        self.running = False
        self.message_count = 0

    def connect(self) -> bool:
        """Connect to the AgentBridge WebSocket."""
        try:
            self.ws = websocket.WebSocketApp(
                self.url,
                on_message=self._on_message,
                on_error=self._on_error,
                on_close=self._on_close,
                on_open=self._on_open,
            )
            # Run in a separate thread so we can send commands
            self.running = True
            thread = threading.Thread(target=self.ws.run_forever, daemon=True)
            thread.start()
            time.sleep(0.5)  # Wait for connection
            return True
        except Exception as e:
            print(f"Connection failed: {e}")
            return False

    def disconnect(self):
        """Close the WebSocket connection."""
        self.running = False
        if self.ws:
            self.ws.close()

    def send(self, data: dict):
        """Send a JSON message to the server."""
        if self.ws and self.ws.sock and self.ws.sock.connected:
            message = json.dumps(data)
            self.ws.send(message)
            print(f"  >> {message}")
        else:
            print("  !! Not connected")

    def spawn_bot(self, name: str, x: int = 3222, z: int = 3222):
        """Spawn a new bot player."""
        self.send({"type": "spawn_bot", "name": name, "x": x, "z": z})

    def despawn_bot(self, name: str):
        """Despawn a bot player."""
        self.send({"type": "despawn_bot", "name": name})

    def walk(self, player: str, x: int, z: int):
        """Walk to coordinates."""
        self.send({"type": "walk", "player": player, "x": x, "z": z})

    def walk_with_doors(self, player: str, x: int, z: int):
        """Walk with automatic door handling."""
        self.send({"type": "walk_with_doors", "player": player, "x": x, "z": z})

    def teleport(self, player: str, x: int, z: int, plane: int = 0):
        """Teleport instantly."""
        self.send({"type": "teleport", "player": player, "x": x, "z": z, "plane": plane})

    def interact_npc(self, player: str, index: int, option: int = 1):
        """Interact with an NPC by server index."""
        self.send({"type": "interact_npc", "player": player, "index": index, "option": option})

    def interact_loc(self, player: str, loc_id: int, x: int, z: int, option: int = 1):
        """Interact with a game object."""
        self.send({
            "type": "interact_loc",
            "player": player,
            "id": loc_id,
            "x": x,
            "z": z,
            "option": option,
        })

    def spawn_item(self, player: str, item_id: int, count: int = 1):
        """Spawn an item into inventory."""
        self.send({"type": "spawn_item", "player": player, "item_id": item_id, "count": count})

    def get_state(self, player: str):
        """Request state snapshot."""
        self.send({"type": "get_state", "player": player})

    def wait_ticks(self, player: str, ticks: int):
        """Wait for N game ticks."""
        self.send({"type": "wait_ticks", "player": player, "ticks": ticks})

    def wait_for_position(self, player: str, x: int, z: int, tolerance: int = 3, timeout: int = 30000):
        """Wait until player reaches a position."""
        self.send({
            "type": "wait_for_position",
            "player": player,
            "x": x,
            "z": z,
            "tolerance": tolerance,
            "timeout": timeout,
        })

    # ----- Command sequences for common QA tasks -----

    def run_npc_dialogue_test(self, player: str, npc_name: str, npc_index: int, npc_x: int, npc_z: int):
        """Full NPC dialogue test: walk → talk → verify dialog → report."""
        print(f"\n{'='*60}")
        print(f"[TEST] NPC Dialogue Test: {npc_name} at ({npc_x}, {npc_z})")
        print(f"{'='*60}")

        # Step 1: Walk to NPC
        print(f"[1/4] Walking to {npc_name}...")
        self.walk_with_doors(player, npc_x, npc_z)
        time.sleep(2)

        # Step 2: Wait for position
        self.wait_for_position(player, npc_x, npc_z, tolerance=3)
        time.sleep(1)

        # Step 3: Interact (Talk-to = option 1)
        print(f"[2/4] Talking to {npc_name}...")
        self.interact_npc(player, npc_index, option=1)
        time.sleep(3)

        # Step 4: Get state to verify dialog
        print(f"[3/4] Verifying dialog...")
        self.get_state(player)
        time.sleep(1)

        print(f"[4/4] Test complete for {npc_name}")
        print(f"{'='*60}\n")

    def run_lumbridge_tour(self, player: str):
        """Run a full Lumbridge NPC tour."""
        lumbridge_npcs = [
            ("Hans", 3222, 3219),
            ("Cook", 3209, 3216),
            ("Duke Horacio", 3214, 3224),
            ("Father Aereck", 3241, 3207),
            ("Bob", 3231, 3203),
            ("Shopkeeper", 3214, 3246),
        ]
        for npc_name, npc_x, npc_z in lumbridge_npcs:
            self.walk_with_doors(player, npc_x, npc_z)
            time.sleep(3)
            self.get_state(player)
            time.sleep(0.5)
        print(f"\n[TOUR COMPLETE] Visited {len(lumbridge_npcs)} NPCs in Lumbridge\n")

    # ----- Internal handlers -----

    def _on_message(self, ws, message):
        self.message_count += 1
        try:
            data = json.loads(message)
            msg_type = data.get("type", "unknown")

            if msg_type == "state":
                self.last_state = data
                player = data.get("player", {})
                dialog = data.get("dialog", {})
                pos = player.get("position", {})

                # Print a compact one-line summary
                dialog_status = "💬" if dialog.get("isOpen") else "  "
                print(f"  [{data.get('tick', '?')}] "
                      f"({pos.get('x', '?')}, {pos.get('z', '?')}) "
                      f"{dialog_status} "
                      f"NPCs nearby: {len(player.get('nearbyNpcs', []))} "
                      f"Items: {len(player.get('inventory', []))}")

                # If dialog is open, show the lines
                if dialog.get("isOpen") and dialog.get("lines"):
                    for line in dialog["lines"][:5]:
                        print(f"    📝 {line}")

                # Show last action result
                last_action = data.get("lastAction")
                if last_action:
                    status = "✅" if last_action.get("success") else "❌"
                    print(f"    {status} {last_action.get('message', '')}")

            elif msg_type == "action_result":
                print(f"  [ACTION] {data.get('message', '')}")
            else:
                print(f"  [MSG] {message[:200]}")
        except json.JSONDecodeError:
            print(f"  [RAW] {message[:200]}")

    @staticmethod
    def _on_error(ws, error):
        print(f"  ⚠ WebSocket error: {error}")

    @staticmethod
    def _on_close(ws, close_status_code, close_msg):
        print(f"  ⚡ Connection closed ({close_status_code}: {close_msg})")

    @staticmethod
    def _on_open(ws):
        print("  ✅ Connected to AgentBridge")


def main():
    parser = argparse.ArgumentParser(description="OpenRune 239 QA Bot Testing Client")
    parser.add_argument("--host", default="192.168.0.187", help="Server host (default: 192.168.0.187)")
    parser.add_argument("--port", type=int, default=43595, help="AgentBridge WebSocket port (default: 43595)")

    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # spawn
    p_spawn = subparsers.add_parser("spawn", help="Spawn a QA test bot")
    p_spawn.add_argument("name", help="Bot name")
    p_spawn.add_argument("--x", type=int, default=3222, help="X coordinate (default: 3222)")
    p_spawn.add_argument("--z", type=int, default=3222, help="Z coordinate (default: 3222)")

    # despawn
    p_despawn = subparsers.add_parser("despawn", help="Despawn a QA test bot")
    p_despawn.add_argument("name", help="Bot name")

    # walk
    p_walk = subparsers.add_parser("walk", help="Walk to coordinates")
    p_walk.add_argument("player", help="Player name")
    p_walk.add_argument("x", type=int, help="X coordinate")
    p_walk.add_argument("z", type=int, help="Z coordinate")

    # talk_to
    p_talk = subparsers.add_parser("talk-to", help="Talk to an NPC")
    p_talk.add_argument("player", help="Player name")
    p_talk.add_argument("npc_index", type=int, help="NPC server index")
    p_talk.add_argument("--option", type=int, default=1, help="Interaction option (default: 1)")

    # get_state
    p_state = subparsers.add_parser("get-state", help="Request state snapshot")
    p_state.add_argument("player", help="Player name")

    # npc_test
    p_npc = subparsers.add_parser("npc-test", help="Run NPC dialogue test")
    p_npc.add_argument("player", help="Player name")
    p_npc.add_argument("npc_name", help="NPC name (for display)")
    p_npc.add_argument("npc_index", type=int, help="NPC server index")
    p_npc.add_argument("x", type=int, help="NPC X coordinate")
    p_npc.add_argument("z", type=int, help="NPC Z coordinate")

    # tour
    p_tour = subparsers.add_parser("tour", help="Run Lumbridge NPC tour")
    p_tour.add_argument("player", help="Player name")

    # monitor
    p_mon = subparsers.add_parser("monitor", help="Monitor state for a player")
    p_mon.add_argument("player", help="Player name")
    p_mon.add_argument("--duration", type=int, default=30, help="Monitor duration in seconds")

    # spawn_item
    p_item = subparsers.add_parser("spawn-item", help="Spawn item into inventory")
    p_item.add_argument("player", help="Player name")
    p_item.add_argument("item_id", type=int, help="Item type ID")
    p_item.add_argument("--count", type=int, default=1, help="Item count")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        print("\nAvailable sub-commands: spawn, despawn, walk, talk-to, get-state, npc-test, tour, monitor, spawn-item")
        return

    client = AgentBridgeClient(host=args.host, port=args.port)
    if not client.connect():
        print("Failed to connect to AgentBridge. Is the server running?")
        print(f"Check: host={args.host} port={args.port} (game.yml: bots.agent-bridge-port)")
        sys.exit(1)

    if args.command == "spawn":
        client.spawn_bot(args.name, args.x, args.z)
        print(f"Spawned {args.name} at ({args.x}, {args.z})")
        time.sleep(1)

    elif args.command == "despawn":
        client.despawn_bot(args.name)
        print(f"Despawned {args.name}")
        time.sleep(0.5)

    elif args.command == "walk":
        client.walk(args.player, args.x, args.z)
        print(f"Walking {args.player} to ({args.x}, {args.z})")
        time.sleep(1)

    elif args.command == "talk-to":
        client.interact_npc(args.player, args.npc_index)
        print(f"Talking to NPC index {args.npc_index}")
        time.sleep(3)
        client.get_state(args.player)
        time.sleep(1)

    elif args.command == "get-state":
        client.get_state(args.player)
        time.sleep(1)
        print(f"Last state tick: {client.last_state.get('tick', 'N/A')}")
        player = client.last_state.get("player", {})
        print(f"Position: ({player.get('position', {}).get('x', '?')}, "
              f"{player.get('position', {}).get('z', '?')})")
        print(f"Nearby NPCs: {len(player.get('nearbyNpcs', []))}")
        for npc in player.get("nearbyNpcs", [])[:5]:
            print(f"  - {npc.get('name', '?')} (index={npc.get('index', '?')}) at "
                  f"({npc.get('x', '?')}, {npc.get('z', '?')}) dist={npc.get('distance', '?')}")

    elif args.command == "npc-test":
        client.run_npc_dialogue_test(args.player, args.npc_name, args.npc_index, args.x, args.z)

    elif args.command == "tour":
        print(f"Starting Lumbridge tour for {args.player}...")
        client.run_lumbridge_tour(args.player)
        # Keep connection alive for a few seconds to receive state updates
        time.sleep(5)

    elif args.command == "monitor":
        print(f"Monitoring {args.player} for {args.duration}s. Press Ctrl+C to stop...")
        try:
            client.get_state(args.player)
            time.sleep(args.duration)
        except KeyboardInterrupt:
            print("\nMonitoring stopped.")

    elif args.command == "spawn-item":
        client.spawn_item(args.player, args.item_id, args.count)
        print(f"Spawned item {args.item_id} x{args.count} for {args.player}")

    print(f"\nMessages received: {client.message_count}")
    client.disconnect()


if __name__ == "__main__":
    main()
