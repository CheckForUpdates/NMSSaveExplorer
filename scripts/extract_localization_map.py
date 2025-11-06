#!/usr/bin/env python3
"""
Produce a map of item IDs → {name, icon} for entries that have a matching icon resource.

The script scans localisation tables to resolve display names, then walks the data
tables (products, substances, technologies, etc.) to collect every entry that has
an icon pointing at an asset available in the supplied icon directory.
"""

from __future__ import annotations

import argparse
import html
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, Iterator, Optional, Sequence
from xml.etree import ElementTree as ET

ICON_PREFIX = "TEXTURES/UI/FRONTEND/ICONS/"
TARGET_ENTRY_TYPES = {
    "GcProductData",
    "GcTechnologyData",
    "GcRealitySubstanceData",
    "GcProceduralTechnologyData",
    "GcModularCustomisationProduct",
}


@dataclass
class ItemEntry:
    item_id: str
    name_token: Optional[str]
    icon_filename: str
    source_file: Path


def parse_localisation(file_path: Path, language: str, fallbacks: Sequence[str]) -> Dict[str, str]:
    """Return a map of localisation token -> display string from the given file."""
    try:
        tree = ET.parse(file_path)
    except ET.ParseError as exc:
        print(f"[warn] Failed to parse {file_path}: {exc}", file=sys.stderr)
        return {}

    entries: Dict[str, str] = {}
    for entry in tree.findall(".//Property[@name='Table'][@value='TkLocalisationEntry']"):
        token = None
        values = {}
        for prop in entry.findall("Property"):
            name = prop.attrib.get("name", "")
            value = prop.attrib.get("value", "").strip()
            if not value:
                continue
            values[name] = html.unescape(value)
            if name == "Id":
                token = value.upper()

        if not token:
            continue

        text = values.get(language)
        if not text:
            for fallback in fallbacks:
                text = values.get(fallback)
                if text:
                    break

        if text:
            entries[token] = text

    return entries


def collect_localisation(root: Path, language: str, fallbacks: Sequence[str]) -> Dict[str, str]:
    tokens: Dict[str, str] = {}
    for file_path in sorted(root.rglob("*.MXML")):
        file_tokens = parse_localisation(file_path, language, fallbacks)
        # Only fill gaps with new values to keep the first non-empty entry.
        for key, value in file_tokens.items():
            tokens.setdefault(key, value)
    return tokens


def iter_item_entries(file_path: Path) -> Iterator[ItemEntry]:
    try:
        tree = ET.parse(file_path)
    except ET.ParseError as exc:
        print(f"[warn] Failed to parse {file_path}: {exc}", file=sys.stderr)
        return

    for element in tree.findall(".//Property"):
        value_type = element.attrib.get("value")
        if value_type not in TARGET_ENTRY_TYPES:
            continue

        item_id = element.attrib.get("_id") or ""
        name_token: Optional[str] = None
        icon_filename: Optional[str] = None

        for child in element.findall("Property"):
            name = child.attrib.get("name")
            value = child.attrib.get("value", "").strip()
            if name == "ID" and value and not item_id:
                item_id = value
            elif name == "Name" and value:
                name_token = value
            elif name == "NameLower" and value and not name_token:
                name_token = value
            elif name == "Icon":
                icon_filename = extract_icon_filename(child)

        if not item_id or not icon_filename:
            continue

        yield ItemEntry(
            item_id=item_id,
            name_token=name_token,
            icon_filename=icon_filename,
            source_file=file_path,
        )


def extract_icon_filename(icon_element: ET.Element) -> Optional[str]:
    for child in icon_element.findall("Property"):
        if child.attrib.get("name") == "Filename":
            value = child.attrib.get("value", "").strip()
            if value:
                return value
    return None


def normalise_icon_path(filename: str, icons_root: Path) -> Optional[str]:
    trimmed = filename.strip()
    if not trimmed:
        return None

    if not trimmed.upper().startswith(ICON_PREFIX):
        return None

    relative = trimmed[len(ICON_PREFIX) :]
    relative = relative.replace("\\", "/")

    candidates = []
    lower = relative.lower()
    if lower.endswith(".dds"):
        candidates.append(lower[:-4] + ".png")
    candidates.append(lower)

    original = relative
    if original.lower().endswith(".dds"):
        candidates.append(original[:-4] + ".png")
    candidates.append(original)

    for candidate in candidates:
        if (icons_root / candidate).exists():
            return candidate

    return None


def resolve_name(token: Optional[str], localisation: Dict[str, str]) -> Optional[str]:
    if not token:
        return None

    upper = token.upper()
    if upper in localisation:
        return localisation[upper]

    # Sometimes the localisation table uses mixed casing.
    if token in localisation:
        return localisation[token]

    # If we could not resolve via localisation, fall back to the literal value
    # when it does not look like a token.
    if not token.startswith("UI_") and "_" not in token:
        return token

    return None


def build_item_map(
    data_root: Path,
    icons_root: Path,
    localisation: Dict[str, str],
) -> Dict[str, Dict[str, str]]:
    mapping: Dict[str, Dict[str, str]] = {}

    for file_path in sorted(data_root.rglob("*.MXML")):
        for entry in iter_item_entries(file_path):
            icon_path = normalise_icon_path(entry.icon_filename, icons_root)
            if not icon_path:
                continue

            display_name = resolve_name(entry.name_token, localisation) or entry.name_token or entry.item_id
            item_id = entry.item_id.upper()
            existing = mapping.get(item_id)
            if existing:
                if existing["icon"] != icon_path or existing["name"] != display_name:
                    print(
                        "[warn] Duplicate item id "
                        f"{item_id!r} in {file_path}, keeping earlier value from {existing.get('source')}",
                        file=sys.stderr,
                    )
                continue

            mapping[item_id] = {
                "name": display_name,
                "icon": icon_path,
                "source": str(entry.source_file),
            }

    # Drop the debugging source field before returning a clean map.
    return {item_id: {"name": data["name"], "icon": data["icon"]} for item_id, data in mapping.items()}


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Create an ID → {name, icon} map from No Man's Sky data tables with existing icon assets."
    )
    parser.add_argument(
        "data_root",
        type=Path,
        help="Directory containing the game's MXML data tables.",
    )
    parser.add_argument(
        "--icons-root",
        type=Path,
        default=Path("src/main/resources/icons"),
        help="Directory that mirrors the icon resources (default: src/main/resources/icons).",
    )
    parser.add_argument(
        "--language",
        default="USEnglish",
        help="Localisation language field to prefer (default: USEnglish).",
    )
    parser.add_argument(
        "--fallback",
        action="append",
        default=["English"],
        help="Fallback localisation field(s) when the primary language is empty. Can be repeated.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Optional JSON file to write the resulting map.",
    )
    parser.add_argument(
        "--pretty",
        action="store_true",
        help="Pretty-print JSON output.",
    )
    args = parser.parse_args()

    if not args.data_root.exists():
        parser.error(f"{args.data_root} does not exist.")
    if not args.icons_root.exists():
        parser.error(f"{args.icons_root} does not exist.")

    localisation = collect_localisation(args.data_root, args.language, args.fallback)
    item_map = build_item_map(args.data_root, args.icons_root, localisation)

    json_kwargs = {"indent": 2, "sort_keys": True} if args.pretty else {"separators": (",", ":"), "sort_keys": True}
    json_output = json.dumps(item_map, **json_kwargs)

    if args.output:
        args.output.write_text(json_output, encoding="utf-8")
    else:
        try:
            print(json_output)
        except BrokenPipeError:
            pass


if __name__ == "__main__":
    main()
