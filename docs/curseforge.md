# CurseForge publishing

Set the project name to **Villager Colonies**. Use the [400 × 400 PNG project icon](../src/main/resources/villager-colonies.png) for the CurseForge thumbnail.

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

The maintainer chooses when to change the version; **0.1.0.1** is the first public bugfix update. For each release, increase the relevant part and reset every part to its right to zero. The numbers track releases, not a percentage of completion. Edit `mod_version` in `gradle.properties`; Gradle uses it for the JAR filename and NeoForge metadata, and CI uses it for the downloadable artifact name. A version change merged into `main` publishes a GitHub release only after the build, world tests and dedicated-server checks pass. CurseForge submission remains a separate step.

`0.1.0.0` is a one-time reset from the internal `0.14.0-alpha` numbering. Earlier development history stays under its original numbers. This does not remove features or change the mod ID or saved-data formats. Version comparators consider the new number lower than the old one, so existing testers must replace the old JAR manually and use matching client/server builds. Keep only one Villager Colonies JAR in each `mods` directory.

The numeric version has no `-alpha` suffix. The leading `0` and the project description communicate the mod's development status. CurseForge's separate **Release Type** field controls which distribution channel a particular file uses: a **Release** file can be a playable build of a mod that is still being developed.

## File upload

1. Download the JAR from [GitHub Releases](https://github.com/Swishhyy/Villager-Colonies/releases/latest), build with `./gradlew build`, or download the artifact from a successful [GitHub Actions build](https://github.com/Swishhyy/Villager-Colonies/actions/workflows/build.yml). CI produces `wwmc-0.1.0.1-neoforge-mc26.2` and uploads only the distributable mod JAR.
2. If downloaded from Actions, extract the artifact ZIP. Upload **`wwmc-0.1.0.1.jar`**, not the artifact ZIP, a sources JAR, or a test JAR.
3. Set **Display Name** to **Villager Colonies 0.1.0.1 - NeoForge 26.2**.
4. Select **Minecraft 26.2** and the **NeoForge** loader, and use the **MIT License** already present in this repository.
5. Set **Release Type** to **Release** for this playable public build. Keep the early-development notice in the description: this channel choice does not declare the mod complete. Release files sync to the CurseForge app by default and are used by the default download button. A new project with only Alpha files is available on the website; an approved Beta or Release file is required for the project to appear in the app, and Beta/Alpha installs require users to opt into those channels.
6. Use the changelog below and submit the file. Save the corrected project summary and use the dashboard's submission controls to send the project back for moderation. Approval remains CurseForge's decision.

### 0.1.0.1 changelog

- Recover injured citizens from outside loaded range and retry failed recalls correctly.
- Finish precise walking approaches so workers can reach pantries, job barrels and furniture without stopping short.
- Allow citizen recovery onto slabs, dirt paths and carpets.
- Explain hauling problems when Courier jobs are disabled, unstaffed or unavailable.
- Add configurable worker/recovery diagnostics and recipe error stack traces in server logs.
- Include full job barrels and missing repair/enchanting materials in town Needs.
- Fix reachable job barrels and work furniture being rejected on bottom slabs and carpets.
- Show filled job places separately from support station counts and explain unemployment, disabled jobs and waiting places.
- Clarify missing versus inaccessible job barrels and show full hover text beside action buttons.
- Preserve existing town saves, jobs and inventories. Install the same build on the client and server.

### 0.1.0.0 changelog

- Start the four-part public version series at **0.1.0.0**; Villager Colonies remains in early development.
- Carry forward the existing settlement, production, courier, trade, alliance, guard, hospital, campaign, research, and outpost systems from the prior development build.
- Include job outfits and work effects, colored town flags and corner banners, the visual Settlement Guide, and tutorial advancements.
- Replace the generic mod description with a gameplay summary and add source/issue links.
- Name CI downloads with the mod version, loader, and Minecraft version, and include only the distributable JAR.

## Publishing references

- [CurseForge project submission guide](https://support.curseforge.com/support/solutions/articles/9000199552)
- [CurseForge file and project types](https://support.curseforge.com/support/solutions/articles/9000197242-file-project-types-and-additional-fields)
- [NeoForge versioning guidance](https://docs.neoforged.net/docs/gettingstarted/versioning/)
