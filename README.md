# EZclimates

**Seasons you can see, from [EZvac](https://github.com/EZvac-Plugin/EZvac)'s climate.** An official EZvac addon.

- **Rain and snow where EZvac says.** While it rains, snow falls wherever EZvac's air is at or below 33 °F, and rain elsewhere. The season, the height and the time of day all count: the plains snow in a cold winter, a mountain snows at the top and rains at its foot, and a desert gets rain in a thunderstorm (EZvac's `weather.dry-areas`).
- **Leaves and grass through the year:** fresh in spring, as vanilla in summer, yellowing in early autumn, orange-red in late autumn, dull in winter. Broadleaf forests turn most, jungles barely. Birch and spruce leaves keep their fixed colours, as in vanilla.
- **Snow that settles and melts.** Snow builds up on open ground round players while it snows, and melts layer by layer once the air is above 34 °F.
- **Ice on cold water:** ponds, lakes, rivers, the sea and unheated pools freeze over at the surface when EZvac's water gets to 32 °F (28 °F for the sea), one block thick, never under a boat or a swimmer. A heated pool never freezes. It thaws back into water above 34 °F.
- **Nothing stays forever.** Every block of snow and ice EZclimates places is recorded in its chunk, and only recorded blocks melt, so a player's own snow and the world's own snow and ice are never touched. A chunk that was unloaded through a thaw catches up as it loads.
- **The world keeps its own biomes.** The seasons are seasonal copies of the biomes, shown only in what each player is sent. Removing EZclimates leaves no trace: run `/ezclimates clear` first to melt what it placed.
- **No re-logging.** When the season turns or rain turns to snow, the chunks players already have are resent, nearest first, and the client redraws them in place.

## Requirements

- Paper or Purpur **26.1 or newer** (Minecraft 26.x only).
- **EZvac BETA 1.0 Pilot 4** or newer (API 1.9).
- **[packetevents](https://github.com/retrooper/packetevents) 2.14.0** or newer, installed as its own plugin.
- Java 25.

Install the jar in `plugins/` and **restart** (not `/reload`): the seasonal biomes are written and loaded before the worlds, so the first start needs a full restart. So does changing the colours in `config.yml`.

## Commands

`/ezclimates` (permission `ezclimates.admin`, ops by default):

| Command | What it does |
| --- | --- |
| `/ezclimates status` | The seasonal biomes, each world's stage, and the snow and ice recorded in loaded chunks |
| `/ezclimates here` | What EZclimates sees where you stand: what's falling, the air, the snow line, the water and its freezing point (`here <x> <z>` from the console) |
| `/ezclimates clear` | Melts every recorded block of snow and ice, in loaded chunks now and in others as they load, and places no more: the way to uninstall cleanly. Lasts across restarts |
| `/ezclimates resume` | Back to normal after `clear` |

## How it works

At start-up EZclimates reads the server's own vanilla biome files and writes a datapack of seasonal copies of the 52 surface overworld biomes: 5 stages × rain, snow or dry, 728 in all, named like `ezclimates:plains/autumn_late_snow` so F3 still says which biome it is. Each copy differs only in its temperature, precipitation and colours; spawns, sky, fog, music and water stay. Paper loads the pack, and packetevents swaps each chunk section's biomes for their copies in what each player is sent, by EZvac's rain-or-snow edge (a copy per 4×4 biome cell, so rain and snow meet along a wavy line on the land, never in layers above you) and the year's progress. Nothing of Mojang's ships in the jar.

The server still uses the real biomes, so a player in a desert thunderstorm sees rain but isn't wet by the server's count.

## Settings

See [`config.yml`](src/main/resources/config.yml): rain and snow, the colour stages and how strongly each kind of biome turns, snow (radius, maximum layers), ice, and per-tick budgets.

## Licence

MIT. EZclimates is released on GitHub only.
