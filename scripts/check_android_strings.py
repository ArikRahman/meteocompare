#!/usr/bin/env python3
"""Vérifie les ressources textuelles Android de MeteoCompare sans Gradle.

Usage: python3 scripts/check_android_strings.py

Les références sont recherchées dans les sources Kotlin/Java et les ressources
XML, y compris les tests. Les ressources conservées exclusivement par
réflexion/désignation dynamique doivent être explicitement ajoutées à
DYNAMICALLY_REFERENCED_RESOURCES, avec une justification.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ANDROID_APP = PROJECT_ROOT / "app"
RESOURCES = ANDROID_APP / "src/main/res"

# Ajouter ici une exception documentée si getIdentifier() ou une API externe
# commence à résoudre une ressource par son nom. Aucune exception à ce jour.
DYNAMICALLY_REFERENCED_RESOURCES: set[tuple[str, str]] = set()

REFERENCE_RE = re.compile(
    r"\bR\.(string|plurals|array)\.([A-Za-z_][A-Za-z0-9_]*)\b"
    r"|@(?:\+)?(string|plurals|array)/([A-Za-z_][A-Za-z0-9_]*)\b"
)
XML_TAG_TYPES = {"string": "string", "plurals": "plurals", "string-array": "array", "integer-array": "array"}
SOURCE_EXTENSIONS = {".kt", ".java", ".xml"}


def resource_definitions() -> tuple[dict[tuple[str, str], Path], list[str]]:
    default: dict[tuple[str, str], Path] = {}
    translated: dict[tuple[str, str], list[Path]] = defaultdict(list)
    errors: list[str] = []
    locale_keys: dict[str, set[tuple[str, str]]] = defaultdict(set)

    for directory in sorted(RESOURCES.glob("values*")):
        for path in sorted(directory.glob("strings*.xml")):
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError as exc:
                errors.append(f"XML invalide {path.relative_to(PROJECT_ROOT)} : {exc}")
                continue
            for element in root:
                kind = XML_TAG_TYPES.get(element.tag)
                name = element.get("name")
                if kind is None or not name:
                    continue
                key = kind, name
                if key in locale_keys[directory.name]:
                    errors.append(f"Clé dupliquée : {directory.name}/{name}")
                locale_keys[directory.name].add(key)
                if directory.name == "values":
                    if key in default:
                        errors.append(f"Définition par défaut dupliquée : {name}")
                    default[key] = path
                else:
                    translated[key].append(path)
    for key, sources in translated.items():
        if key not in default:
            for path in sources:
                errors.append(f"Traduction sans ressource par défaut : {key[1]} ({path.relative_to(PROJECT_ROOT)})")
    return default, errors


def resource_references() -> set[tuple[str, str]]:
    referenced: set[tuple[str, str]] = set()
    for source_set in ("main", "test", "androidTest"):
        source_dir = ANDROID_APP / "src" / source_set
        if not source_dir.exists():
            continue
        for path in source_dir.rglob("*"):
            if not path.is_file() or path.suffix not in SOURCE_EXTENSIONS:
                continue
            if path.parent.name.startswith("values") and path.name.startswith("strings"):
                # Les traductions sont des déclarations, pas des références.
                continue
            content = path.read_text(encoding="utf-8")
            if path.suffix in {".kt", ".java"}:
                content = re.sub(r"/\*.*?\*/", "", content, flags=re.DOTALL)
                content = re.sub(r"(?m)^\s*//.*$", "", content)
            else:
                content = re.sub(r"<!--.*?-->", "", content, flags=re.DOTALL)
            for match in REFERENCE_RE.finditer(content):
                resource_type = match.group(1) or match.group(3)
                name = match.group(2) or match.group(4)
                referenced.add((resource_type, name))

    # Les ressources traduites peuvent faire référence à d'autres chaînes.
    for path in RESOURCES.glob("values*/strings*.xml"):
        content = path.read_text(encoding="utf-8")
        for match in REFERENCE_RE.finditer(content):
            resource_type = match.group(1) or match.group(3)
            name = match.group(2) or match.group(4)
            referenced.add((resource_type, name))
    return referenced


def main() -> int:
    definitions, errors = resource_definitions()
    references = resource_references() | DYNAMICALLY_REFERENCED_RESOURCES
    unused = sorted(definitions.keys() - references)
    missing = sorted(references - definitions.keys())
    for kind, name in unused:
        errors.append(f"Ressource inutilisée : {kind}/{name}")
    for kind, name in missing:
        errors.append(f"Référence sans définition locale : {kind}/{name}")

    if errors:
        print("Audit des ressources Android : ÉCHEC")
        print("\n".join(f" - {error}" for error in errors))
        return 1

    print(f"Audit des ressources Android : OK ({len(definitions)} clés définies et référencées)")
    print("Ressources mortes : 0 ; références cassées : 0 ; traductions orphelines : 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
