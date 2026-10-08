# CurseForge publishing

## Project summary

Paste this single line into the **General -> Summary** field:

> Build and manage villager settlements, automate production, trade between towns, and command guards against raids and bandit camps.

It describes several playable features and fits the field's 256-character limit. The same summary appears in the mod's in-game description.

## Project description

The text between the markers is ready to paste into the project's **Description** field using Markdown mode.

<!-- curseforge-description:start -->
## World War MC

World War MC (WWMC) brings first-person settlement management and strategy to Minecraft. Found a town, live alongside its citizens, organize production, and lead your guards into the field.

### Build a working settlement

- Recruit named citizens and give them permanent jobs at dedicated stations.
- Set up farms, lumber work, mines, quarries, cooking, smelting, crafting, enchanting, and equipment repair.
- Use warehouses, job barrels, and couriers to move real supplies through your town.
- Keep citizens fed, provide housing, and build hospital beds for injured citizens to recover.
- Expand your town with station, production, and population upgrades.

### Trade, cooperate, and defend

- Establish physical trade routes between towns; traders carry goods through the world.
- Invite friends as builders or stewards, manage claim permissions, and form mutual alliances.
- Equip guards, set patrols, raise alarms, and defend against hostile waves.
- Lead guard squads to bandit camps, occupied mines, and ruined forts, then establish supplied outposts.
- Fund settlement projects and research with materials from your warehouses.

### Learn while you play

Job outfits, work animations, particles, and sounds show what citizens are doing. Town colors and corner banners mark your settlement. A visual Settlement Guide, tutorial advancements, needs board, and shared settlement map help you get started.

Craft the **Settlement Guide** from **one book and one blue dye**, right-click it to open the handbook, and press **L** to view WWMC's advancement tree.

### Requirements and development status

- **Minecraft Java Edition 26.2**
- **NeoForge 26.2.0.88 or newer for Minecraft 26.2**
- **Java 25**
- Install the same WWMC version on the client and server when playing multiplayer.

**WWMC is in early development.** Version **0.1.0.0** starts the public version series and includes the settlement and campaign features above. Larger warfare systems, countries, autonomous building, and distant settlement simulation remain planned features. Balance and systems may change; use a test world or back up an existing world before updating.

[Source code](https://github.com/Swishhyy/World-War-MC) · [Report a bug](https://github.com/Swishhyy/World-War-MC/issues)
<!-- curseforge-description:end -->

## Public version format

WWMC uses a custom four-part format: **release.milestone.update.hotfix**. It is not strict three-part Semantic Versioning.

| Part | Meaning | Example |
| --- | --- | --- |
| Release | Keep `0` while the mod is in development; `1` begins the first completed release | `1.0.0.0` |
| Milestone | A substantial new gameplay system or development milestone | `0.2.0.0` |
| Update | A normal feature, improvement, balance, or maintenance update | `0.1.1.0` |
| Hotfix | A focused correction to the current update | `0.1.0.1` |

Increase only the relevant part, reset every part to its right to zero, and never reuse a public version for different JAR contents. The numbers track releases, not a percentage of completion. Edit `mod_version` in `gradle.properties`; Gradle uses it for the JAR filename and NeoForge metadata, and CI uses it for the downloadable artifact name.

`0.1.0.0` is a one-time reset from the internal `0.14.0-alpha` numbering. Earlier development history stays under its original numbers. This does not remove features or change the mod ID or saved-data formats. Version comparators consider the new number lower than the old one, so existing testers must replace the old JAR manually and use matching client/server builds. Keep only one WWMC JAR in each `mods` directory.

The numeric version has no `-alpha` suffix. The leading `0` and the project description communicate the mod's development status. CurseForge's separate **Release Type** field controls which distribution channel a particular file uses: a **Release** file can be a playable build of a mod that is still being developed.

## File upload

1. Build with `./gradlew build`, or download the artifact from a successful [GitHub Actions build](https://github.com/Swishhyy/World-War-MC/actions/workflows/build.yml). CI produces `wwmc-0.1.0.0-neoforge-mc26.2` and uploads only the distributable mod JAR.
2. If downloaded from Actions, extract the artifact ZIP. Upload **`wwmc-0.1.0.0.jar`**, not the artifact ZIP, a sources JAR, or a test JAR.
3. Set **Display Name** to **World War MC 0.1.0.0 - NeoForge 26.2**.
4. Select **Minecraft 26.2** and the **NeoForge** loader, and use the **MIT License** already present in this repository.
5. Set **Release Type** to **Release** for this playable public build. Keep the early-development notice in the description: this channel choice does not declare the mod complete. Release files sync to the CurseForge app by default and are used by the default download button. A new project with only Alpha files is available on the website; an approved Beta or Release file is required for the project to appear in the app, and Beta/Alpha installs require users to opt into those channels.
6. Use the changelog below and submit the file. Save the corrected project summary and use the dashboard's submission controls to send the project back for moderation. Approval remains CurseForge's decision.

### 0.1.0.0 changelog

- Start the four-part public version series at **0.1.0.0**; WWMC remains in early development.
- Carry forward the existing settlement, production, courier, trade, alliance, guard, hospital, campaign, research, and outpost systems from the prior development build.
- Include job outfits and work effects, colored town flags and corner banners, the visual Settlement Guide, and tutorial advancements.
- Replace the generic mod description with a gameplay summary and add source/issue links.
- Name CI downloads with the mod version, loader, and Minecraft version, and include only the distributable JAR.

## Publishing references

- [CurseForge project submission guide](https://support.curseforge.com/support/solutions/articles/9000199552)
- [CurseForge file and project types](https://support.curseforge.com/support/solutions/articles/9000197242-file-project-types-and-additional-fields)
- [NeoForge versioning guidance](https://docs.neoforged.net/docs/gettingstarted/versioning/)
