"""Verify the tested release JAR and extract its version's changelog."""

import hashlib
import os
from pathlib import Path
import re
import sys
import tomllib
import zipfile


def prepare_release(artifact_dir: Path) -> None:
    root = Path(__file__).resolve().parents[1]
    properties = dict(
        line.strip().split("=", 1)
        for line in (root / "gradle.properties").read_text().splitlines()
        if "=" in line and not line.lstrip().startswith("#")
    )
    version = properties["mod_version"]
    if not re.fullmatch(r"\d+\.\d+\.\d+\.\d+", version):
        raise ValueError("Public releases require a four-part numeric version")
    if os.environ.get("WWMC_VERSION", version) != version:
        raise ValueError("The tested artifact version differs from the checked-out source")

    jar = artifact_dir / f"wwmc-{version}.jar"
    if sorted(artifact_dir.glob("*.jar")) != [jar]:
        raise ValueError("Expected only the distributable mod JAR")
    with zipfile.ZipFile(jar) as archive:
        metadata = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode())
        mods = metadata["mods"]
        if len(mods) != 1 or mods[0]["modId"] != "wwmc" or mods[0]["version"] != version:
            raise ValueError("JAR metadata does not match the release version")
        if archive.testzip() is not None:
            raise ValueError("The release JAR contains a corrupt entry")

    changelog = (root / "docs/CHANGELOG.md").read_text()
    section = re.search(rf"(?ms)^## {re.escape(version)}\s*\n(.*?)(?=^## |\Z)", changelog)
    if section is None or not section.group(1).strip():
        raise ValueError(f"No release notes found for {version}")
    requirements = (
        f"Requires Minecraft Java **{properties['minecraft_version']}**, "
        f"NeoForge **{properties['neo_version']} or newer for that Minecraft version**, "
        "and **Java 25**. Download the JAR below and replace the old WWMC JAR in `mods`. "
        "Use matching versions on the server and every client.\n\n"
    )
    (artifact_dir / "release-notes.md").write_text(requirements + section.group(1).strip() + "\n")
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    jar.with_suffix(".jar.sha256").write_text(f"{digest}  {jar.name}\n")
    print(f"Verified {jar.name}; release notes and SHA-256 checksum are ready.")


if __name__ == "__main__":
    prepare_release(Path(sys.argv[1]))
