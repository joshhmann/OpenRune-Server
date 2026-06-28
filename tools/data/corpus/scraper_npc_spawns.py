#!/usr/bin/env python3
"""
OSRS NPC Spawn Scraper v2 — extracts {{LocLine}} coordinates from OSRS wiki.

Pipeline:
  1. Get ALL pages that use {{Infobox Monster}} via embeddedin API
  2. For each page, fetch wikitext and extract {{LocLine}} templates
  3. Parse coordinates from each LocLine
  4. Cross-reference with mejrs cache IDs for completeness
  5. Save to corpus parsed-data/npc_spawns.json

Usage:
    python3 scraper_npc_spawns.py --dry-run         # Preview only
    python3 scraper_npc_spawns.py --max 10          # Scrape first 10
    python3 scraper_npc_spawns.py                   # Full run
"""

import argparse, json, os, re, sys, time, urllib.request, urllib.parse
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

CORPUS = Path("/var/lib/pixelrag/osrs")
WIKI_API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "OSRS-Corpus-Scraper/2.0 (rsmod project)"
DELAY = 0.35

# ── API helper ──────────────────────────────────────────────────
def req(params: dict, retries: int = 3) -> dict:
    params["format"] = "json"
    url = WIKI_API + "?" + urllib.parse.urlencode(params, doseq=True)
    r = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(r, timeout=30) as resp:
                return json.loads(resp.read())
        except Exception as e:
            if attempt == retries - 1:
                raise
            print(f"  [retry {attempt+1}] {e}")
            time.sleep(2 ** attempt)

# ── Phase 1: get all monster pages ──────────────────────────────
def get_all_monster_pages(max_pages: int = None) -> list:
    """Get all pages using {{Infobox Monster}}."""
    titles = []
    eicontinue = None
    while True:
        params = {
            "action": "query",
            "list": "embeddedin",
            "eititle": "Template:Infobox Monster",
            "eilimit": "500",
        }
        if eicontinue:
            params["eicontinue"] = eicontinue
        
        data = req(params)
        pages = data.get("query", {}).get("embeddedin", [])
        for p in pages:
            title = p["title"]
            if title not in titles:
                titles.append(title)
                if max_pages and len(titles) >= max_pages:
                    return titles
        
        eicontinue = data.get("continue", {}).get("eicontinue")
        if not eicontinue:
            break
        time.sleep(DELAY)
    
    return titles

# ── Phase 2: extract LocLine data ───────────────────────────────
def parse_loclines(wikitext: str) -> list:
    """Extract {{LocLine}} entries with coordinates."""
    entries = []
    pattern = r'\{\{LocLine\s*\n(.*?)\n\}\}'
    
    for match in re.finditer(pattern, wikitext, re.DOTALL):
        block = match.group(1)
        entry = {"params": {}, "coords": []}
        
        for line in block.split('\n'):
            line = line.strip()
            m = re.match(r'\|\s*(\w+)\s*=\s*(.*)', line)
            if m:
                entry["params"][m.group(1)] = m.group(2).strip()
        
        for cm in re.finditer(r'\|x:(\d+),y:(\d+)', block):
            x, y = int(cm.group(1)), int(cm.group(2))
            plane = int(entry["params"].get("plane", 0))
            entry["coords"].append({"x": x, "y": y, "plane": plane})
        
        if entry["coords"]:
            entries.append(entry)
    
    return entries

def fetch_monster_spawns(page_title: str) -> dict:
    """Fetch a monster page and extract all spawn locations."""
    data = req({"action": "parse", "page": page_title, "prop": "wikitext", "formatversion": "2"})
    parse = data.get("parse", {})
    wikitext = parse.get("wikitext", "")
    if not wikitext:
        return None
    
    loclines = parse_loclines(wikitext)
    if not loclines:
        return None
    
    # Count distinct locations
    locations = defaultdict(int)
    total_coords = 0
    for ll in loclines:
        loc = ll["params"].get("location", "unknown")
        # Strip wiki formatting
        loc_clean = re.sub(r'\[\[([^\]|]+)(?:\|[^\]]+)?\]\]', r'\1', loc)
        locations[loc_clean] += len(ll["coords"])
        total_coords += len(ll["coords"])
    
    return {
        "page_title": page_title,
        "total_spawns": total_coords,
        "location_count": len(locations),
        "locations": dict(locations),
        "loclines": loclines,
    }

# ── Main ────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--max", type=int, default=None)
    parser.add_argument("--out", default=str(CORPUS / "parsed-data" / "npc_spawns.json"))
    args = parser.parse_args()
    
    print("Phase 1: Getting all Infobox Monster pages...")
    pages = get_all_monster_pages(args.max)
    print(f"  Found {len(pages)} monster pages")
    
    if args.dry_run:
        print("\nFirst 10:")
        for p in pages[:10]:
            print(f"  {p}")
        return
    
    # Scrape each page
    spawns = {}
    errors = []
    
    print(f"\nPhase 2: Scraping {len(pages)} monster pages...")
    for i, title in enumerate(pages):
        print(f"  [{i+1}/{len(pages)}] {title}", end="")
        try:
            result = fetch_monster_spawns(title)
            if result:
                spawns[title] = result
                locs = result["location_count"]
                print(f"  ({result['total_spawns']} spawns, {locs} locations)")
            else:
                print("  (no LocLine data)")
        except Exception as e:
            errors.append((title, str(e)))
            print(f"  ERROR: {e}")
        
        time.sleep(DELAY)
        
        if (i + 1) % 100 == 0:
            _save(args.out, spawns, len(pages), errors, True, i + 1)
    
    _save(args.out, spawns, len(pages), errors)

def _save(path, spawns, total, errors, progress=False, done=0):
    output = {
        "meta": {
            "source": "OSRS Wiki Infobox Monster → LocLine",
            "scraped_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "total_pages": total,
            "scraped": done if progress else total,
            "pages_with_spawns": len(spawns),
            "errors": len(errors),
        },
        "monster_spawns": spawns,
    }
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(output, indent=2))
    if progress:
        print(f"  [CKPT] {p} ({p.stat().st_size//1024}KB, {done}/{total})")
    else:
        print(f"\nSaved: {p} ({p.stat().st_size//1024}KB)")

if __name__ == "__main__":
    main()
