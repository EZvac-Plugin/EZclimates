# EZclimates

**Seasons you can see and feel, from [EZvac](https://github.com/EZvac-Plugin/EZvac)'s climate.** An official EZvac addon.

- **Rain and snow where EZvac says.** While it rains, snow falls wherever EZvac's air is at or below 33 °F, and rain elsewhere. The season, the height and the time of day all count: the plains snow in a cold winter, a mountain snows at the top and rains at its foot, and a desert gets rain in a thunderstorm (EZvac's `weather.dry-areas`).
- **Leaves and grass through the year:** fresh in spring, as vanilla in summer, yellowing in early autumn, orange-red in late autumn, dull in winter. Broadleaf forests turn most, jungles barely. Birch and spruce leaves keep their fixed colours, as in vanilla.
- **Snow that settles and melts.** Snow builds up on open ground round players while it snows, and melts layer by layer once the air is above 34 °F.
- **Ice on cold water:** ponds, lakes, rivers, the sea and unheated pools freeze over at the surface when EZvac's water gets to 32 °F (28 °F for the sea), one block thick, never under a boat or a swimmer. A heated pool never freezes. It thaws back into water above 34 °F.
- **Nothing stays forever.** Every block of snow and ice EZclimates places is recorded in its chunk, and only recorded blocks melt, so a player's own snow and the world's own snow and ice are never touched. A chunk that was unloaded through a thaw catches up as it loads.
- **The world keeps its own biomes.** The seasons are seasonal copies of the biomes, shown only in what each player is sent. Removing EZclimates leaves no trace: run `/ezclimates clear` first to melt what it placed.
- **The weather, planned.** EZclimates plans each world's weather days ahead and drives Minecraft's to match, so EZvac's weather stations forecast what will really happen: fronts of rain or snow between dry spells by the season, **thunderstorm fronts**, **snowstorms** in winter (heavy snow, a gale, a dark sky without lightning, snow piling up fast), and **heat waves** and **cold snaps** that move EZvac's air for days, so rooms, HVAC, rain or snow and lakes all feel them. Today and tomorrow are certain; later days can still shift, and forecasts say so. `/weather` and sleeping through a storm still work.
- **Crops that grow by the air.** Crops, stems, cocoa, sugar cane and saplings grow by EZvac's air where they stand: not at all at 36 °F or colder, slowly in cool air, at their usual pace from 60 °F, half as fast again in warm humid air, and slower in great heat. The reading is the one a thermometer there gives, so **a greenhouse an EZvac system keeps warm grows through winter** while the field outside waits for spring. `/ezclimates here` says how fast crops grow where you stand, and why.
- **The ground through the year.** In spring, flowers come up on open grass round players once the air reaches 45 °F (wildflowers among them in meadows, plains and birch woods) and pink petals under cherry trees; flowers last through summer and die back in autumn, petals go after spring. In autumn, leaf litter gathers under oak, birch and other broadleaf trees and lies through winter, and mushrooms come up under trees. Each is recorded like snow, so only EZclimates' own ever go, and a flower a player planted stays.
- **Body temperature** (LiveExposure, now part of EZclimates; off until you switch it on). Every player's body rests at 98.6 °F, falls in the cold and rises in the heat by EZvac's feels-like, the sun and the shade included, and settles once they're comfortable. Stay out too long and it hurts: slowness, weakness, then damage, with an optional sickness system. See [Body temperature](#body-temperature).
- **Switched from EZvac's Addons menu.** `/hvac addons` switches EZclimates on and off while the server runs, and each part on its own: seasonal looks, snow and ice, planned weather, plants, and body temperature.
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
| `/ezclimates weather` | The weather plan for the next week: each day's chances, events and shifts |
| `/ezclimates weather event <snowstorm\|thunderstorm\|heatwave\|coldsnap> [hours]` | Starts one now (12 in-game hours by default) |
| `/ezclimates weather replan` | Plans the weather afresh from now |
| `/ezclimates here` | What EZclimates sees where you stand: what's falling, the air, the snow line, the water and its freezing point (`here <x> <z>` from the console) |
| `/ezclimates body reload` | Reads `body.yml` again |
| `/ezclimates body set <player> <°F>` | Sets a player's body temperature |
| `/ezclimates body sick <player> <mild\|moderate\|severe>`, `cure <player>`, `roll <player>` | Makes a player ill, cures them, or rolls their chance of falling ill now |
| `/ezclimates adopt [blocks]` | Takes on thin snow nobody recorded within that many blocks of you (64 by default), so it melts like EZclimates' own: for strays left by a server that was killed mid-snowfall. Only in biomes where Minecraft itself never snows |
| `/ezclimates clear` | Melts every recorded block of snow and ice, in loaded chunks now and in others as they load, and places no more: the way to uninstall cleanly. Lasts across restarts |
| `/ezclimates resume` | Back to normal after `clear` |

`/bodytemp` (permission `liveexposure.use`, everyone by default) shows your own body temperature and any sickness.

## Switching it and its parts

EZvac's Addons menu (`/hvac addons`) lists EZclimates. The dye under it switches all of it; its icon opens its parts:

| Part | Switched off |
| --- | --- |
| Seasonal looks | Players see the world's own biomes again (their chunks are resent as they are) |
| Snow and ice | Nothing new is placed, Minecraft's own snow is let be, and what is there still melts |
| Planned weather | The plan is dropped and Minecraft's weather runs itself |
| Plants | Crops grow as in vanilla, and no flowers or mushrooms come up (those already up go) |
| Body temperature | Nobody's body changes. This one starts off |

The choices are kept in EZvac's `config.yml` through restarts.

## Body temperature

This was the LiveExposure addon. It is off until you switch it on in `/hvac addons` (EZclimates, Body temperature), because by default it can kill (`lethal` in `body.yml`).

- **Coming from LiveExposure:** remove its jar and leave its folder. On the next start EZclimates brings `plugins/LiveExposure/config.yml` over to `plugins/EZclimates/body.yml` and starts with body temperature on, and each player's body temperature and illness carry over as they join. While the LiveExposure jar is still installed, EZclimates leaves body temperature to it.
- **The sun and the shade** come from EZvac's feels-like now (BETA 1.0 Pilot 4), so LiveExposure's own `shade` setting is gone.
- **Kept as they were:** `/bodytemp`, the `liveexposure.use` and `liveexposure.bypass` permissions, and every other setting. `/liveexposure …` is `/ezclimates body …`.
- **On EZvac's HUD** the section is `{section:ezclimates:body}`; a HUD format that named `{section:liveexposure:body}` needs that changed.
- LiveExposure 0.2.0 stays available for Minecraft 1.21.x servers, which EZclimates doesn't run on.

## How it works

At start-up EZclimates reads the server's own vanilla biome files and writes a datapack of seasonal copies of the 52 surface overworld biomes: 5 stages × rain, snow or dry, 728 in all, named like `ezclimates:plains/autumn_late_snow` so F3 still says which biome it is. Each copy differs only in its temperature, precipitation and colours; spawns, sky, fog, music and water stay. Paper loads the pack, and packetevents swaps each chunk section's biomes for their copies in what each player is sent, by EZvac's rain-or-snow edge (a copy per 4×4 biome cell, so rain and snow meet along a wavy line on the land, never in layers above you) and the year's progress. Nothing of Mojang's ships in the jar.

The server still uses the real biomes, so a player in a desert thunderstorm sees rain but isn't wet by the server's count.

## Settings

See [`body.yml`](src/main/resources/body.yml) for body temperature, and [`config.yml`](src/main/resources/config.yml) for the rest: plants (the temperatures crops grow between, flowers and mushrooms), rain and snow, the colour stages and how strongly each kind of biome turns, snow (radius, maximum layers, rate), ice, the weather (each season's dry spells, fronts and thunder, snowstorms, heat waves, cold snaps, and how sure forecasts are), and per-tick budgets. The plan is kept in `weather.yml`.

## Licence

MIT. EZclimates is released on GitHub only.
