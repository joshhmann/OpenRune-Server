import asyncio
import sys
import os

# Include parent directory in python path to import bot_client
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "scripts")))

from bot_client import BotClient

async def run_woodcutting_test():
    print("=== Starting Woodcutting QA Test ===")
    
    # Spawn a QA Bot named "QATester"
    client = BotClient("QATester")
    
    try:
        print("Connecting to OpenRune AgentBridge...")
        await client.connect()
        print("Bot spawned successfully.")
        
        # Teleport to Lumbridge Castle courtyard near trees
        print("Teleporting to Lumbridge Castle (3222, 3222)...")
        await client.teleport(3222, 3222)
        
        # Spawn a bronze axe
        print("Spawning bronze axe...")
        await client.spawn_item("bronze_axe", 1)
        
        # Interact with a nearby tree object (ID: 1276)
        print("Interacting with tree (ID: 1276) at (3223, 3222)...")
        await client.interact_loc(1276, 3223, 3222)
        
        # Wait for Woodcutting XP gain (25 XP is 1 log chopped)
        print("Waiting for Woodcutting XP drop...")
        success = await client.wait_for_xp("woodcutting", 25, timeout_seconds=20)
        
        if success:
            print("SUCCESS: Bot successfully chopped a log and gained XP!")
        else:
            print("FAILED: Timeout waiting for XP drop.")
            
    except Exception as e:
        print(f"ERROR: Exception occurred during test: {e}")
    finally:
        print("Disconnecting and despawning bot...")
        await client.disconnect()
        print("=== Test finished ===")

if __name__ == "__main__":
    asyncio.run(run_woodcutting_test())
