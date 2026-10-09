# CurseForge publishing

[Project overview](../README.md) · [All guides](README.md) · [CurseForge project](https://www.curseforge.com/minecraft/mc-mods/world-war-mc)

## Project summary

Paste this single line into the **General -> Summary** field:

> Build and manage villager settlements, automate production, trade between towns, and command guards against raids and bandit camps.

It describes several playable features and fits the field's 256-character limit. The same summary appears in the mod's in-game description.

## Project description

The text between the markers is ready to paste into the project's **Description** field using Markdown mode.

<!-- curseforge-description:start -->
## Villager Colonies

Villager Colonies lets you build and manage your own settlement in Minecraft.

Recruit villagers and give them jobs like farming, mining, cooking, and crafting. Keep your people fed, organize supplies, and grow your town.

Trade with other settlements, work together with friends, and equip guards to defend against raids. When you're ready, lead your guards out to clear bandit camps and establish outposts.

An in-game guide and advancements help you get started.

Research the Stone, Bronze and Iron Ages together, discover furnished ruins, and build traps to support your guards.

**Villager Colonies is playable but still in development**, with more features and improvements planned.

Requires **Minecraft Java 26.2 and NeoForge 26.2.0.88 or newer for 26.2**, using **Java 25**. Install the mod on both the client and server for multiplayer.

[Project guides](https://github.com/Swishhyy/Villager-Colonies/blob/main/docs/README.md) · [Report a bug](https://github.com/Swishhyy/Villager-Colonies/issues)
<!-- curseforge-description:end -->

## Public version format

Villager Colonies uses a custom four-part format: **release.milestone.update.hotfix**. It is not strict three-part Semantic Versioning.

| Part | Meaning | Example |
| --- | --- | --- |
| Release | Keep `0` while the mod is in development; `1` begins the first completed release | `1.0.0.0` |
| Milestone | A substantial new gameplay system or development milestone | `0.2.0.0` |
| Update | A normal feature, improvement, balance, or maintenance update | `0.1.1.0` |
| Hotfix | A focused correction to the current update | `0.1.0.1` |

The maintainer chooses when to change the version. The renamed mod restarts at **0.1.0.0**, retaining every earlier feature and bug fix. For each later release, increase the relevant part and reset every part to its right to zero. The numbers track releases, not a percentage of completion.

Edit `mod_version` in `gradle.properties`. Gradle uses it for the JAR and NeoForge metadata; `mod_archive_name=villager-colonies` controls the filename independently of the saved `wwmc` namespace. CI checks the filename, display name, internal ID, version and changelog on every build. A version change merged into `main` publishes a GitHub release only after all checks pass. Future tags use **`villager-colonies-v<version>`** so restarting the version does not collide with historical WWMC tags. CurseForge submission remains a separate step.

This development branch stays open and unmerged until the maintainer is ready. Older WWMC releases and their changelogs retain their original names and numbers. The internal mod ID, resource IDs, commands, `wwmc-server.toml` and saved-data namespace stay compatible. Replace the old JAR manually and use matching client/server builds; keep only one Villager Colonies or WWMC JAR in each `mods` directory.

The numeric version has no `-alpha` suffix. The leading `0` and the project description communicate the mod's development status. CurseForge's separate **Release Type** field controls which distribution channel a particular file uses: a **Release** file can be a playable build of a mod that is still being developed.

## File upload

These settings are prepared for **Villager Colonies 0.1.0.0**. The renamed build is currently a branch test artifact; the published legacy release is still World War MC 0.1.0.1.

1. Build with `./gradlew build` or download the artifact from a successful [GitHub Actions build of the development branch](https://github.com/Swishhyy/Villager-Colonies/actions?query=branch%3Acodex%2F0.1.1.0-villager-ai). CI produces `villager-colonies-0.1.0.0-neoforge-mc26.2` and uploads only the distributable mod JAR. Published builds appear on [GitHub Releases](https://github.com/Swishhyy/Villager-Colonies/releases/latest).
2. If downloaded from Actions, extract the artifact ZIP. Upload **`villager-colonies-0.1.0.0.jar`**, not the artifact ZIP, a sources JAR, or a test JAR.
3. Set **Display Name** to **Villager Colonies 0.1.0.0 - NeoForge 26.2**.
4. Select **Minecraft 26.2** and the **NeoForge** loader, and use the **MIT License** already present in this repository.
5. When ready to publish, set **Release Type** to **Release** for the playable build. Keep the development notice in the description.
6. Use the changelog below and submit the file. Save the corrected project summary and use the dashboard's submission controls to send the project back for moderation. Approval remains CurseForge's decision.

### 0.1.0.0 changelog

- Rename the mod to **Villager Colonies** and restart at **0.1.0.0**, keeping existing features, bug fixes and saves.
- Add shared Stone, Bronze and Iron Age research, researcher jobs, tin ore and bronze equipment.
- Add five traps with saved wear, rearming, repairs and craftsman maintenance.
- Improve wave approaches, guard defense, worker paths, lumberjacks and civilian sheltering.
- Add furnished ruins, 3D carcasses, clearer work animations and improved armor rendering.
- Add population research and quiet banner/journal notices.
- Use the same build on the client and server. Replace the previous WWMC JAR.

See the [full changelog](CHANGELOG.md) for detailed changes and historical WWMC releases.

## Publishing references

- [CurseForge project submission guide](https://support.curseforge.com/support/solutions/articles/9000199552)
- [CurseForge file and project types](https://support.curseforge.com/support/solutions/articles/9000197242-file-project-types-and-additional-fields)
- [NeoForge versioning guidance](https://docs.neoforged.net/docs/gettingstarted/versioning/)
