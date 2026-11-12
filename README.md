# Minecraft Plugin: Preston Nether Jester Titan

Preston: Nether Jester Titan is a Minecraft plugin for Paper servers that adds a neutral flying Nether boss encounter with a generated castle, boss bar, melee attacks, spear rain, lightning attacks, custom equipment, ally conversion, hostile-mob protection, and configurable boss-fight music.

## Install

1. Use a Paper server running Minecraft 1.21.11 or a compatible 1.21 build.
2. Install Java 21.
3. Build the plugin with Maven:

```bash
mvn package
```

4. Copy `target/plugin.jar` to the server's `plugins` directory.
5. Start or restart the server.
6. Review `plugins/Preston/config.yml` and restart the server after changing configuration.

The plugin requires the Paper API and does not include the `WAR` music audio. To play custom music, provide a server resource pack containing the configured `preston:war` sound key.

## Use

1. Enter the Nether.
2. Use the command:

```text
/preston spawn
```

3. Preston's Nether castle is created at your location and Preston spawns inside it.
4. Preston remains neutral until attacked.
5. Attack Preston to begin the boss fight. Nearby players receive a boss bar and the configured boss music.
6. During the fight, Preston uses his axe, spear rain, lightning strikes, flight, and Nether-themed visual effects.
7. Defeat Preston to convert him into an ally. The player who dealt the final hit becomes his owner.
8. Alternatively, interact with Preston while holding a Nether Star and a Dragon Egg. Each item is consumed individually, and Preston becomes an ally after accepting both.
9. As an ally, Preston follows his owner, teleports back when he gets too far away, targets nearby hostile mobs, and protects his owner.

The `/preston spawn` command requires the `preston.spawn` permission, which defaults to server operators.

---

More Minecraft plugin projects by EnderDevelopment: https://github.com/EnderDevelopment

Project page: https://dash.enderdevelopment.com/community/4da052f8-332e-4222-86f0-ef8e93be7b22?utm_source=github&utm_medium=readme&utm_campaign=auto_publish&utm_content=4da052f8-332e-4222-86f0-ef8e93be7b22
