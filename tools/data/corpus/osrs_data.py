"""
OSRS Research Corpus — Agent Data Loader.

Quick access for sub-agents building RSMod server features.
Usage:
    from osrs_data import OSRS

    db = OSRS()
    db.quests["Cook's Assistant"]           # quest lookup
    db.item_by_id[314]                      # item by game ID
    db.monsters["Abyssal demon"]            # monster stats
    db.drop_sources["Abyssal demon"]        # drop table
    db.quest_deps["Dragon Slayer II"]        # prerequisites
    db.progression.lookup("Dragon Slayer II")  # ironman guide phase
    db.skill_reqs(60, "mining")              # quests that gate on 60 mining
"""

import json, os, sys, re
from pathlib import Path
from typing import Any, Optional

ROOT = Path(__file__).parent

# ── Auto-loaded datasets ──────────────────────────────────────

class _LazyLoader:
    """Loads a JSON file on first access and caches it."""

    def __init__(self, path: Path):
        self._path = path
        self._data = None

    def _load(self):
        if self._data is None:
            with open(self._path, encoding="utf-8") as f:
                self._data = json.load(f)
        return self._data

    def get(self) -> Any:
        return self._load()

    def get_key(self, key, default=None):
        return self._load().get(key, default)


class _ProgressionIndex:
    """Fast lookup from quest name -> ironman guide phase."""

    def __init__(self, data: dict):
        self._phase_map = {}
        for phase in data.get("phases", []):
            phase_name = phase["phase"]
            for q in phase.get("key_quests", []):
                self._phase_map[q.lower()] = phase_name
            for s in phase.get("key_skills", {}):
                pass  # skills indexed separately below

    def lookup(self, quest_name: str) -> Optional[str]:
        return self._phase_map.get(quest_name.lower())

    def phase_of(self, quest_name: str) -> Optional[dict]:
        """Returns the full phase dict a quest belongs to."""
        pname = self.lookup(quest_name)
        return pname  # caller can iterate phases themselves


class OSRS:
    """Single entry-point for all OSRS scraped data."""

    def __init__(self, root: Path = ROOT):
        self._root = root

        # ── Master index ──
        self._index = _LazyLoader(root / "_index.json")

        # ── Parsed (cleaned) datasets ──
        self.quests = _LazyLoader(root / "parsed-data" / "quest_lookup.json").get()["quests"]
        self.item_by_name = _LazyLoader(root / "parsed-data" / "item_by_name.json").get()["items"]
        # Normalize item_by_id keys: wiki bucket returns stringified lists like "['4151']"
        raw_items = _LazyLoader(root / "parsed-data" / "item_by_id.json").get()["items"]
        normalized_items = {}
        for key, val in raw_items.items():
            ids = [s.strip().strip("'\"") for s in key.strip("[]").split(",")]
            for id_str in ids:
                normalized_items[id_str] = val
        self.item_by_id = normalized_items
        self.monsters = _LazyLoader(root / "parsed-data" / "monster_by_name.json").get()["monsters"]
        self.npc_by_name = _LazyLoader(root / "parsed-data" / "npc_by_name.json").get()["npcs"]
        raw_npcs = _LazyLoader(root / "parsed-data" / "npc_by_id.json").get()["npcs"]
        normalized_npcs = {}
        for key, val in raw_npcs.items():
            ids = [s.strip().strip("'\"") for s in key.strip("[]").split(",")]
            for id_str in ids:
                normalized_npcs[id_str] = val
        self.npc_by_id = normalized_npcs
        self.drop_sources = _LazyLoader(root / "parsed-data" / "drops_by_source.json").get()["drops"]
        self.quest_progression = _LazyLoader(root / "parsed-data" / "quest_progression.json").get()
        self.monster_compendium = _LazyLoader(root / "parsed-data" / "monster_compendium.json").get()
        self.npc_directory = _LazyLoader(root / "parsed-data" / "npc_directory.json").get()
        self.item_index = _LazyLoader(root / "parsed-data" / "item_index.json").get()
        self.spellbook = _LazyLoader(root / "parsed-data" / "spellbook.json").get()
        self.shops = _LazyLoader(root / "parsed-data" / "shops.json").get()
        self.construction = _LazyLoader(root / "parsed-data" / "construction.json").get()

        # ── Dependency trees ──
        self.quest_deps = _LazyLoader(root / "dependencies" / "quest_dependencies.json").get()
        self.reverse_deps = _LazyLoader(root / "dependencies" / "reverse_dependencies.json").get()
        self.progression_graph = _LazyLoader(root / "dependencies" / "progression_graph.json").get()

        # ── Skill data ──
        self.skill_xp = _LazyLoader(root / "skills" / "experience_table.json").get()
        self.quest_skill_reqs = _LazyLoader(root / "skills" / "quest_skill_requirements.json").get()

        # ── Ironman guide phases ──
        self._progression_data = _LazyLoader(root / "ironman-guide" / "progression_timeline.json").get()
        self.progression = _ProgressionIndex(self._progression_data)

    # ── Convenience queries ──

    def quest(self, name: str) -> Optional[dict]:
        """Look up a quest by exact or fuzzy name match."""
        exact = self.quests.get(name)
        if exact:
            return exact
        for k, v in self.quests.items():
            if name.lower() in k.lower():
                return v
        return None

    def item(self, *, name: str = None, id: int = None) -> Optional[dict]:
        """Look up an item by name (exact or partial) or by game item ID."""
        if name:
            exact = self.item_by_name.get(name.lower())
            if exact:
                return exact
            for k, v in self.item_by_name.items():
                if name.lower() in k:
                    return v
            return None
        if id is not None:
            return self.item_by_id.get(str(id))
        return None

    def monster(self, name: str) -> Optional[dict]:
        """Look up a monster by name (exact then fuzzy)."""
        exact = self.monsters.get(name.lower())
        if exact:
            return exact
        for k, v in self.monsters.items():
            if name.lower() in k:
                return v
        return None

    def npc(self, *, name: str = None, id: int = None) -> Optional[dict]:
        """Look up an NPC by name or ID."""
        if name:
            exact = self.npc_by_name.get(name.lower())
            if exact:
                return exact
            for k, v in self.npc_by_name.items():
                if name.lower() in k:
                    return v
            return None
        if id is not None:
            return self.npc_by_id.get(str(id))
        return None

    def drops(self, source: str) -> Optional[list]:
        """Get drop table for a monster/NPC by name."""
        return self.drop_sources.get(source)

    def skill_reqs(self, level: int, skill_name: str = None) -> list:
        """
        Find all quests that require a given level in a skill.

        Example:
            db.skill_reqs(60, "mining")     # quests needing 60 mining
            db.skill_reqs(70)                # all quests needing 70+ in any skill
        """
        results = []
        for qname, qdata in self.quests.items():
            for req in qdata.get("skill_requirements", []):
                if isinstance(req, dict):
                    sname = req.get("Skill", "") or req.get("skill", "")
                    slevel = int(req.get("Level", 0) or req.get("level", 0))
                elif isinstance(req, list) and len(req) >= 2:
                    sname, slevel = req[0], int(req[1])
                else:
                    continue
                if skill_name and sname.lower() != skill_name.lower():
                    continue
                if slevel >= level:
                    results.append({"quest": qname, "skill": sname, "level": slevel})
        return sorted(results, key=lambda r: r["level"], reverse=True)

    def drops_for_item(self, item_name: str) -> list:
        """Find all monsters that drop a given item."""
        results = []
        for source, drop_list in self.drop_sources.items():
            for d in drop_list:
                if item_name.lower() in d["item"].lower():
                    results.append({"source": source, **d})
        return results

    def quests_for_level_range(self, min_qp: int = 0, max_qp: int = 999) -> list:
        """Filter progression graph by quest point range."""
        results = []
        for qname, qdata in self.quest_progression.items():
            qp = int(qdata.get("quest_points", 0))
            if min_qp <= qp <= max_qp:
                results.append((qname, qdata))
        return results

    def progression_phase(self, quest_name: str) -> Optional[str]:
        """Which ironman guide phase does a quest belong to?"""
        return self.progression.lookup(quest_name)

    def summary(self) -> str:
        """Print a summary of all loaded datasets."""
        i = self._index.get()
        lines = [f"OSRS Research Corpus — {i.get('purpose', '')}"]
        lines.append(f"  {i.get('total_size_mb', 0)}MB total across {len(i.get('directories', {}))} directories")
        lines.append(f"  Quick-ref entrypoints:")
        for label, path in i.get("quick_ref", {}).items():
            lines.append(f"    {label}: {path}")
        return "\n".join(lines)


# ── Convenience: pre-load at import time ──
db = OSRS()  # instance for `from osrs_data import db`
