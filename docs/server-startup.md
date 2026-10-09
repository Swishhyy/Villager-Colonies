# Dedicated server startup: missing Overworld settings

[Project overview](../README.md) · [All guides](README.md)

The supplied 26.2 NeoForge log loads WWMC successfully, then fails before world startup with:

```text
Unable to read or access the world gen settings file!
./world/data/minecraft/world_gen_settings.dat
IllegalStateException: Overworld settings missing
```

This concerns the world's generation metadata. Changing WWMC versions does not recreate that file safely. The message about WWMC changing from 0.11.3 to 0.12.0 is an upgrade warning; it is not the reported exception.

For an existing NeoForge world:

1. Fully stop the server in AMP and make a copy of the current world folder.
2. Check the `level-name` in `server.properties`. The supplied log names `world`, so AMP expects `/AMP/Minecraft/world/data/minecraft/world_gen_settings.dat`.
3. Check that the file exists, is readable by the server process, and was included in the world upload or restore. If a restore is incomplete, restore the complete same-world backup into a separate folder before selecting it in AMP.
4. If only this metadata file is missing or damaged, restore it from a known good backup of **this exact world**, then start NeoForge again. Do not substitute another world's settings: seed and generation configuration matter for future terrain.
5. If there is no matching backup or readable copy, further recovery needs the world files. Keep the existing world intact. A separate, newly named test world can establish that the installation starts, while preserving the original world for recovery.

If the world previously passed through Paper, also check `world/dimensions/minecraft/overworld/data/minecraft/`. Paper's [official migration instructions](https://docs.papermc.io/paper/migration/#to-vanilla) describe relocating `world_gen_settings.dat`, `game_rules.dat`, `scheduled_events.dat`, `wandering_trader.dat` and `weather.dat` to `world/data/minecraft/` for Vanilla/Forge-style servers. Work from a backup and preserve the source files. This is a possible alternative cause, not established by this log or the user's existing-NeoForge history.

The WWMC build checks a dedicated NeoForge server on an isolated fresh world, saves it, verifies that its generation metadata exists, stops cleanly, and reloads that saved world. Both startup cycles must succeed before CI produces the downloadable mod artifact. They do not modify a player's existing world.
