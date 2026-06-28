"""
OSRS Corpus Tool — fetch new data and keep the corpus index in sync.

Two data sources:
  1) Wiki Bucket API (structured JSON — items, NPCs, monsters, drops, shops, etc.)
  2) Wiki HTML pages (quest guides, mechanics, training guides, etc.)

Every fetch auto-updates _index.json so the corpus stays self-describing.

Usage:
    python scraper.py list-buckets                  # see available bucket templates
    python scraper.py bucket "Infobox item"         # fetch a bucket
    python scraper.py page "Vorkath"                # fetch one page (saves to content/)
    python scraper.py page "Vorkath" --to quests    # save to a specific subdir
    python scraper.py pages titles.txt              # batch fetch from a file
    python scraper.py search "boss"                 # search wiki, print titles
    python scraper.py search "boss" --fetch         # search + fetch all results
    python scraper.py search "boss" --fetch --to quests
    python scraper.py update                        # refresh index from disk
    python scraper.py suggest                       # suggested pages to add
"""

import argparse, json, os, re, sys, time, urllib.request, urllib.parse, urllib.error
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

CORPUS = Path(__file__).parent
WIKI_API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "OSRS-Corpus-Scraper/1.0 (rsmod project; research use)"
REQUEST_DELAY = 0.5

# Which subdirectories the corpus tracks and how to describe them
WATCHED_DIRS = {
    "buckets":    "Structured tabular data from Wiki Bucket API",
    "quests":     "Full quest guide HTML pages (walkthrough, rewards, requirements)",
    "content":    "Game mechanics reference pages (bosses, minigames, areas, etc.)",
    "skills":     "Skill training pages and XP tables",
    "cache-diffs":"Low-level cache revision pages",
    "ironman-guide":"Oziris Ironman Efficiency Guide",
    "dependencies":"Quest dependency trees and progression graph",
    "parsed-data":"RSMod-ready synthesized datasets",
}


# ── Index management ─────────────────────────────────────────────

def _fmt_size(path: Path) -> float:
    """Total size of all files under a directory, in MB."""
    total = 0
    if path.is_file():
        return path.stat().st_size / (1024 * 1024)
    for f in path.rglob("*"):
        if f.is_file():
            total += f.stat().st_size
    return round(total / (1024 * 1024), 1)


def _html_count(path: Path) -> int:
    return len(list(path.glob("*.html")))


def _json_count(path: Path) -> int:
    return len([f for f in path.glob("*.json") if f.name != "_index.json"])


def update_index():
    """Scan corpus disk state and rewrite _index.json."""
    index_path = CORPUS / "_index.json"
    old = {}
    if index_path.exists():
        old = json.loads(index_path.read_text())

    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    directories = {}

    for name, desc in WATCHED_DIRS.items():
        dp = CORPUS / name
        entry = {"path": f"{name}/", "description": desc}
        if not dp.exists():
            entry["size_mb"] = 0
            entry["count"] = 0
            directories[name] = entry
            continue

        entry["size_mb"] = _fmt_size(dp)
        html_files = list(dp.glob("*.html"))
        json_files = [f for f in dp.glob("*.json") if f.name != "_index.json"]

        if html_files:
            entry["count"] = len(html_files)
        elif json_files:
            if len(json_files) == 1:
                entry["file"] = json_files[0].name
            else:
                entry["files"] = sorted(f.name for f in json_files)

        directories[name] = entry

    # Root-level files
    root_files = [f for f in CORPUS.glob("*") if f.is_file() and f.suffix in (".py", ".md", ".txt", ".sh")]
    root_size = _fmt_size(CORPUS) - sum(d.get("size_mb", 0) for d in directories.values())
    root_size = max(0, round(root_size, 1))

    quick_ref = old.get("quick_ref", {})

    index = {
        "dataset": "OSRS RSMod Research Corpus",
        "version": old.get("version", 1) + (1 if old.get("updated") != now else 0),
        "updated": now,
        "purpose": "Comprehensive OSRS Wiki data for RSMod server implementation",
        "total_size_mb": sum(d.get("size_mb", 0) for d in directories.values()) + root_size,
        "total_files": sum(
            (d.get("count", 0) if "count" in d else len(d.get("files", [])) if "files" in d else (1 if "file" in d else 0))
            for d in directories.values()
        ),
        "sources": old.get("sources", {
            "wiki_bucket_api": "https://oldschool.runescape.wiki/api.php (Bucket extension)",
            "wiki_parse_api": "https://oldschool.runescape.wiki/api.php (action=parse)",
            "ironman_guide": "https://ironman.guide/guide (Oziris v4, 2026)",
        }),
        "directories": directories,
        "tools": {
            "scraper": "scraper.py — fetch new data and auto-update index",
            "loader": "osrs_data.py — Python agent data loader (from osrs_data import OSRS)",
            "mapping": "RSMOD_MAPPING.md — data-to-RSMod-plugin mapping guide",
        },
        "quick_ref": quick_ref,
        "how_to_use": old.get("how_to_use", [
            "For structured RSMod data (items, NPCs, monsters, quests): load from parsed-data/",
            "For raw wiki data (all fields): load from buckets/",
            "For quest guides with walkthroughs: load from quests/",
            "For quest dependency trees: load from dependencies/",
            "For skill training methods: load from skills/",
            "For game mechanics: load from content/",
            "For cache revision history: load from cache-diffs/",
            "For Ironman progression order: load from ironman-guide/",
        ]),
    }

    index_path.write_text(json.dumps(index, indent=2), encoding="utf-8")
    kb = index_path.stat().st_size
    print(f"  _index.json updated ({kb} bytes)")


# ── Helpers ─────────────────────────────────────────────────────

def _req(params: dict, retries: int = 3) -> dict:
    params["format"] = "json"
    url = WIKI_API + "?" + urllib.parse.urlencode(params, doseq=True)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read())
        except (urllib.error.HTTPError, urllib.error.URLError, OSError) as e:
            if attempt == retries - 1:
                raise
            print(f"  [retry {attempt+1}/{retries}] {e}")
            time.sleep(2 ** attempt)


def _fetch_html(url: str, retries: int = 3) -> Optional[str]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.read().decode("utf-8", errors="replace")
        except (urllib.error.HTTPError, urllib.error.URLError, OSError) as e:
            if attempt == retries - 1:
                print(f"  [FAIL] {url}: {e}")
                return None
            print(f"  [retry {attempt+1}/{retries}] {e}")
            time.sleep(2 ** attempt)


def _safe_filename(title: str, ext: str = ".html") -> str:
    """Turn a wiki page title into a safe filename."""
    name = re.sub(r"[^a-zA-Z0-9 _-]", "", title).strip().replace(" ", "-").lower()[:120]
    name = re.sub(r"-+", "-", name).strip("-")
    return name + ext


# ── Bucket API ───────────────────────────────────────────────────

def list_buckets():
    data = _req({"action": "bucket", "list": "templates"})
    templates = data.get("bucket", {}).get("templates", [])
    print(f"{len(templates)} available bucket templates:\n")
    for t in sorted(templates):
        print(f"  {t}")
    print('\nFetch with:  python scraper.py bucket "Template Name"')


def fetch_bucket(template: str) -> dict:
    all_rows = {}
    offset = 0
    limit = 5000
    total = 0
    print(f"Fetching bucket: {template}")
    while True:
        data = _req({
            "action": "bucket",
            "bucket": template,
            "limit": str(limit),
            "offset": str(offset),
        })
        rows = data.get("bucket", {}).get("rows", {})
        if not rows:
            break
        all_rows.update(rows)
        total += len(rows)
        offset += limit
        print(f"  {total} rows fetched...")
        time.sleep(REQUEST_DELAY)
        if len(rows) < limit:
            break
    print(f"  Done: {total} total rows")
    return all_rows


def save_bucket(template: str, data: dict):
    safe_name = re.sub(r"[^a-zA-Z0-9]+", "_", template.strip()).strip("_").lower()
    out = CORPUS / "buckets" / f"{safe_name}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"count": len(data), "template": template, "rows": data}, indent=2), encoding="utf-8")
    print(f"  Saved: {out} ({out.stat().st_size // 1024}KB)")
    update_index()


# ── Page fetching ────────────────────────────────────────────────

def search_wiki(query: str, limit: int = 20) -> list:
    data = _req({
        "action": "query",
        "list": "search",
        "srsearch": query,
        "srlimit": str(min(limit, 500)),
    })
    return [p["title"] for p in data.get("query", {}).get("search", [])]


def fetch_page(title: str) -> Optional[str]:
    clean = title.replace(" ", "_")
    url = f"https://oldschool.runescape.wiki/w/{urllib.parse.quote(clean)}"
    html = _fetch_html(url)
    if html:
        print(f"  Fetched: {title} ({len(html)} bytes)")
    return html


def save_page(title: str, html: str, subdir: str):
    out = CORPUS / subdir / _safe_filename(title)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(html, encoding="utf-8")
    print(f"  Saved: {out} ({out.stat().st_size // 1024}KB)")
    update_index()


def batch_fetch(titles: list, subdir: str, skip_existing: bool = True):
    for i, title in enumerate(titles):
        title = title.strip()
        if not title:
            continue
        out = CORPUS / subdir / _safe_filename(title)
        if skip_existing and out.exists():
            print(f"  [{i+1}/{len(titles)}] Skipping: {title}")
            continue
        print(f"  [{i+1}/{len(titles)}] {title}")
        html = fetch_page(title)
        if html:
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(html, encoding="utf-8")
            print(f"  Saved: {out} ({out.stat().st_size // 1024}KB)")
        time.sleep(REQUEST_DELAY)
    update_index()


# ── Suggested topics ─────────────────────────────────────────────

SUGGESTED = {
    "content": [
        "TzTok-Jad", "Zulrah", "Vorkath", "The Corrupted Gauntlet",
        "The Nightmare", "Phantom Muspah", "Tombs of Amascut/Mechanics",
        "Chambers of Xeric/Mechanics", "Theatre of Blood/Mechanics",
        "Fishing Trawler", "Tempoross", "Wintertodt", "Guardians of the Rift",
        "Mahogany Homes", "Giants' Foundry", "Farming/Patch locations",
        "Hunter/Black chinchompa", "Runecrafting/Ourania Altar",
        "Kourend Woodland", "Feldip Hills", "Karamja", "Tirannwn",
        "Morytania", "Wilderness", "Fossil Island", "Zeah", "Prifddinas",
        "Ruins of Camdozaal", "Shayzien", "Lovakengj", "Piscarilius",
        "Hosidius", "Arceuus",
    ],
    "quests": [
        "A Kingdom Divided", "A Night at the Theatre",
        "Beneath Cursed Sands", "Children of the Sun",
        "Curse of the Empty Lord", "Desert Treasure II",
        "Enlightened Journey", "The Garden of Death", "The Giant Dwarf",
        "The Great Brain Robbery", "In Aid of the Myreque",
        "King's Ransom", "Lair of Tarn Razorlor", "The Path of Glouphrie",
        "Recipe for Disaster/Freeing the Brothers", "Secrets of the North",
        "Sins of the Father", "Sleeping Giants", "Swan Song",
        "Temple of Ikov", "Twilight's Promise", "While Guthix Sleeps",
    ],
    "skills": [
        "Agility/Training", "Crafting/Training", "Fletching/Training",
        "Herblore/Training", "Smithing/Training", "Runecrafting/Training",
        "Construction/Training", "Hunter/Training",
    ],
}


# ── CLI ──────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="OSRS Corpus Tool — fetch data, keep index in sync.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    sub = parser.add_subparsers(dest="command")

    sub.add_parser("list-buckets", help="List available bucket templates")

    bp = sub.add_parser("bucket", help="Fetch a wiki bucket by template name")
    bp.add_argument("template", type=str)
    bp.add_argument("--dry-run", action="store_true")

    pp = sub.add_parser("page", help="Fetch a single wiki page as HTML")
    pp.add_argument("title", type=str)
    pp.add_argument("--to", dest="subdir", type=str, default="content",
                    help="Subdirectory to save into (content/, quests/, skills/, pages/)")

    pps = sub.add_parser("pages", help="Batch-fetch pages from a file or comma list")
    pps.add_argument("source", type=str, help="File path (one title per line) or comma-separated list")
    pps.add_argument("--to", dest="subdir", type=str, default="content")
    pps.add_argument("--refetch", action="store_true")

    sp = sub.add_parser("search", help="Search wiki for page titles")
    sp.add_argument("query", type=str)
    sp.add_argument("--limit", type=int, default=20)
    sp.add_argument("--fetch", action="store_true")
    sp.add_argument("--to", dest="subdir", type=str, default="content")

    sub.add_parser("update", help="Refresh _index.json from disk state")
    sub.add_parser("suggest", help="Print suggested pages to add")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return

    if args.command == "list-buckets":
        list_buckets()

    elif args.command == "bucket":
        data = fetch_bucket(args.template)
        if not args.dry_run:
            save_bucket(args.template, data)

    elif args.command == "page":
        html = fetch_page(args.title)
        if html:
            save_page(args.title, html, args.subdir)

    elif args.command == "pages":
        src = args.source
        if os.path.isfile(src):
            titles = [l.strip() for l in open(src) if l.strip()]
        else:
            titles = [t.strip() for t in src.split(",")]
        print(f"Fetching {len(titles)} pages into {args.subdir}/")
        batch_fetch(titles, subdir=args.subdir, skip_existing=not args.refetch)

    elif args.command == "search":
        titles = search_wiki(args.query, args.limit)
        print(f"\n{len(titles)} results for '{args.query}':\n")
        for t in titles:
            print(f"  {t}")
        if args.fetch and titles:
            print(f"\nFetching all {len(titles)} pages...")
            batch_fetch(titles, subdir=args.subdir)

    elif args.command == "update":
        update_index()

    elif args.command == "suggest":
        for subdir, pages in SUGGESTED.items():
            print(f"\n--- {subdir}/ ---\n")
            for t in pages:
                print(f'  scraper.py page "{t}" --to {subdir}')
        print(f'\n  Or batch:\n')
        for subdir in SUGGESTED:
            print(f'  scraper.py pages "{",".join(SUGGESTED[subdir][:3])}..." --to {subdir}')
        print()


if __name__ == "__main__":
    main()
