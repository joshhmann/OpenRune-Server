#!/usr/bin/env python3
"""Check if OSRS wiki corpus has NPC spawn coordinate data."""
import json

paths = [
    '/var/lib/pixelrag/osrs/parsed-data/npc_by_id.json',
    '/var/lib/pixelrag/osrs/parsed-data/npc_directory.json',
    '/var/lib/pixelrag/osrs/buckets/infobox_npc.json',
    '/var/lib/pixelrag/osrs/parsed-data/npc_by_name.json',
]

for path in paths:
    try:
        with open(path) as f:
            data = json.load(f)
    except Exception as e:
        print(f'{path}: FAILED - {e}')
        continue

    if isinstance(data, dict):
        # Check for 'npcs' key or other structure
        for key in list(data.keys())[:5]:
            val = data[key]
            if isinstance(val, list) and len(val) > 0:
                sample = val[0]
                break
            elif isinstance(val, dict) and len(val) > 0:
                sample_key = list(val.keys())[0]
                sample = val[sample_key]
                break
        else:
            sample = data
        
        print(f'\n{path}:')
        if isinstance(sample, dict):
            print(f'  Sample keys: {list(sample.keys())}')
            has_coords = 'x' in sample or 'y' in sample or 'plane' in sample or 'tile' in sample or 'location' in sample
            has_spawn = 'spawn' in str(sample).lower()
            print(f'  Has direct coords: {has_coords}')
            
            # Check if 'location' has wiki-style coord references
            if 'location' in sample:
                loc = str(sample['location'])
                has_wiki_coords = '|x:' in loc or ':' in loc
                print(f'  Location has wiki coords: {has_wiki_coords}')
                if has_wiki_coords:
                    print(f'  Sample location: {loc[:200]}')
        else:
            print(f'  Type: {type(sample)}')
    elif isinstance(data, list):
        print(f'\n{path}:')
        print(f'  Length: {len(data)}')
        if data:
            print(f'  Sample keys: {list(data[0].keys()) if isinstance(data[0], dict) else type(data[0])}')
    else:
        print(f'\n{path}: type={type(data).__name__}')
