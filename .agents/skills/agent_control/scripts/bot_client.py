import json
import asyncio
import websockets
import time

class BotClient:
    def __init__(self, bot_name, host="127.0.0.1", port=43595):
        self.bot_name = bot_name
        self.url = f"ws://{host}:{port}"
        self.ws = None
        self.last_state = None
        self.recv_task = None
        self.connected = False
        self.event_queue = asyncio.Queue()

    async def connect(self):
        """Connect to the AgentBridge and spawn/claim the bot player."""
        self.ws = await websockets.connect(self.url)
        self.connected = True
        self.recv_task = asyncio.create_task(self._recv_loop())
        
        # Spawn the bot player
        await self.send({"type": "spawn_bot", "name": self.bot_name})
        # Wait a tick for spawning to sync
        await self.wait_ticks(1)

    async def disconnect(self):
        """Disconnect and despawn the bot player."""
        self.connected = False
        if self.recv_task:
            self.recv_task.cancel()
        if self.ws:
            # Despawn bot cleanly
            await self.ws.send(json.dumps({"type": "despawn_bot", "name": self.bot_name}))
            await self.ws.close()

    async def send(self, command):
        """Send a JSON command over the websocket."""
        if self.ws and self.connected:
            await self.ws.send(json.dumps(command))

    async def _recv_loop(self):
        try:
            async for msg in self.ws:
                data = json.loads(msg)
                if data.get("type") == "state" and data.get("player", {}).get("name") == self.bot_name:
                    self.last_state = data
                    await self.event_queue.put(data)
        except asyncio.CancelledError:
            pass
        except Exception as e:
            print(f"[BotClient] Recv Loop Exception: {e}")

    async def recv_tick(self, timeout=5.0):
        """Wait for the next tick's state update."""
        try:
            return await asyncio.wait_for(self.event_queue.get(), timeout)
        except asyncio.TimeoutError:
            return None

    async def send_action(self, action_type, **params):
        """Wrapper for sending standard bot actions."""
        params["player"] = self.bot_name
        params["type"] = action_type
        await self.send(params)

    async def wait_ticks(self, ticks):
        """Blocks for a specified number of game ticks."""
        for _ in range(ticks):
            await self.recv_tick()

    async def walk(self, x, z):
        """Walk to destination tiles."""
        await self.send_action("walk", x=x, z=z)
        await self.wait_ticks(1)

    async def teleport(self, x, z, plane=0):
        """Teleport to coordinates."""
        await self.send_action("teleport", x=x, z=z, plane=plane)
        await self.wait_ticks(1)

    async def spawn_item(self, item_id, count=1):
        """Admin spawn item into inventory."""
        await self.send_action("spawn_item", item_id=item_id, count=count)
        await self.wait_ticks(1)

    async def interact_loc(self, id, x, z, option=1):
        """Interact with a static world object (loc) at (x,z)."""
        await self.send_action("interact_loc", id=id, x=x, z=z, option=option)
        await self.wait_ticks(1)

    async def interact_npc(self, index, option=1):
        """Interact with an NPC by entity index."""
        await self.send_action("interact_npc", index=index, option=option)
        await self.wait_ticks(1)

    async def eat_food(self, food_item="obj.sharks"):
        """Eat food to heal."""
        await self.send_action("eat_food", food_item=food_item)
        await self.wait_ticks(1)

    async def wait_for_ready(self, timeout_seconds=10):
        """Wait until player is not in combat or animating."""
        start = time.time()
        while time.time() - start < timeout_seconds:
            state = await self.recv_tick()
            if state:
                player = state.get("player", {})
                if not player.get("animating") and not player.get("inCombat"):
                    return True
        return False

    async def wait_for_xp(self, skill, min_amount, timeout_seconds=15):
        """Wait until bot player gains at least min_amount XP in the target skill."""
        initial_xp = -1
        start = time.time()
        while time.time() - start < timeout_seconds:
            state = await self.recv_tick()
            if state:
                skills = state.get("player", {}).get("skills", {})
                skill_data = skills.get(skill, {})
                xp = skill_data.get("xp", 0)
                if initial_xp == -1:
                    initial_xp = xp
                if xp - initial_xp >= min_amount:
                    return True
        return False
