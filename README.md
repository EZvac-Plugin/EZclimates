<div align="center">

# EZclimates

**Seasons you can see and feel, from EZvac's climate.**<br>
An official addon for [EZvac](https://github.com/EZvac-Plugin/EZvac), real heating, cooling and indoor climate for Paper and Purpur.

[![Release](https://img.shields.io/github/v/release/EZvac-Plugin/EZclimates?label=release&color=0e7c86&logo=github&style=for-the-badge&labelColor=1f2328)](https://github.com/EZvac-Plugin/EZclimates/releases)
[![Needs EZvac](https://img.shields.io/badge/needs%20EZvac-BETA%201.0%20P4%2B-0e7c86?style=for-the-badge&labelColor=1f2328&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI+PHBhdGggZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNOCAxYzIuNCAyLjggNC42IDUuNSA0LjYgOC4yYTQuNiA0LjYgMCAwIDEtOS4yIDBDMy40IDYuNSA1LjYgMy44IDggMXptMCA2LjZhMS43IDEuNyAwIDEgMCAwIDMuNCAxLjcgMS43IDAgMCAwIDAtMy40eiIvPjwvc3ZnPg==)](https://github.com/EZvac-Plugin/EZvac)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1%2B-0e7c86?style=for-the-badge&labelColor=1f2328&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiIgc2hhcGUtcmVuZGVyaW5nPSJjcmlzcEVkZ2VzIj48cmVjdCB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIGZpbGw9IiM4YTVhM2IiLz48cmVjdCB3aWR0aD0iMTYiIGhlaWdodD0iNSIgZmlsbD0iIzZhYWEzYSIvPjxyZWN0IHg9IjIiIHk9IjUiIHdpZHRoPSIyIiBoZWlnaHQ9IjIiIGZpbGw9IiM2YWFhM2EiLz48cmVjdCB4PSI3IiB5PSI1IiB3aWR0aD0iMiIgaGVpZ2h0PSIzIiBmaWxsPSIjNmFhYTNhIi8+PHJlY3QgeD0iMTIiIHk9IjUiIHdpZHRoPSIyIiBoZWlnaHQ9IjIiIGZpbGw9IiM2YWFhM2EiLz48cmVjdCB4PSI0IiB5PSIxMCIgd2lkdGg9IjIiIGhlaWdodD0iMiIgZmlsbD0iIzZiNDMyOSIvPjxyZWN0IHg9IjEwIiB5PSIxMiIgd2lkdGg9IjIiIGhlaWdodD0iMiIgZmlsbD0iIzZiNDMyOSIvPjxyZWN0IHg9IjEyIiB5PSI4IiB3aWR0aD0iMSIgaGVpZ2h0PSIxIiBmaWxsPSIjYTc3NzRmIi8+PHJlY3QgeD0iMiIgeT0iMTMiIHdpZHRoPSIxIiBoZWlnaHQ9IjEiIGZpbGw9IiNhNzc3NGYiLz48L3N2Zz4=)](#requirements)
[![License](https://img.shields.io/github/license/EZvac-Plugin/EZclimates?color=0e7c86&logo=opensourceinitiative&style=for-the-badge&labelColor=1f2328)](LICENSE)

[![Download](https://img.shields.io/badge/Download-f69500?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI+PHBhdGggZmlsbD0iI2ZmZiIgZD0iTTcgMWgydjdsMi41LTIuNSAxLjQgMS40TDggMTEuOCAzLjEgNi45bDEuNC0xLjRMNyA4eiIvPjxwYXRoIGZpbGw9IiNmZmYiIGQ9Ik0yIDEyaDJ2MS41aDhWMTJoMnYzLjVIMnoiLz48L3N2Zz4=)](https://github.com/EZvac-Plugin/EZclimates/releases/latest)
[![EZvac](https://img.shields.io/badge/EZvac-f69500?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI+PHBhdGggZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNOCAxYzIuNCAyLjggNC42IDUuNSA0LjYgOC4yYTQuNiA0LjYgMCAwIDEtOS4yIDBDMy40IDYuNSA1LjYgMy44IDggMXptMCA2LjZhMS43IDEuNyAwIDEgMCAwIDMuNCAxLjcgMS43IDAgMCAwIDAtMy40eiIvPjwvc3ZnPg==)](https://github.com/EZvac-Plugin/EZvac)
[![Website](https://img.shields.io/badge/EZvac%20website-f69500?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI+PHBhdGggZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNOCAxYTcgNyAwIDEgMCAwIDE0QTcgNyAwIDAgMCA4IDF6bS0uOCAxLjZDNi40IDMuNyA1LjkgNS4zIDUuOCA3LjJIMy4xYTUgNSAwIDAgMSA0LjEtNC42em0xLjYgMGE1IDUgMCAwIDEgNC4xIDQuNmgtMi43Yy0uMS0xLjktLjYtMy41LTEuNC00LjZ6TTcuMiA3LjJINy4zYy4xLTEuNi41LTMgLjctMy42LjIuNi42IDIgLjcgMy42ek0zLjEgOC44aDIuN2MuMSAxLjkuNiAzLjUgMS40IDQuNmE1IDUgMCAwIDEtNC4xLTQuNnptNC4yIDBoMS40Yy0uMSAxLjYtLjUgMy0uNyAzLjYtLjItLjYtLjYtMi0uNy0zLjZ6bTMuNiAwaDIuN2E1IDUgMCAwIDEtNC4xIDQuNmMuOC0xLjEgMS4zLTIuNyAxLjQtNC42eiIvPjwvc3ZnPg==)](https://ezvac-plugin.github.io/EZvac/)

</div>

## Features

<table>
<tr>
<td width="50%" valign="top">

**🌨️ Rain and snow where EZvac says**<br>
Snow falls wherever EZvac's air is cold enough, by the season, the height and the time of day.

</td>
<td width="50%" valign="top">

**🍂 Leaves and grass through the year**<br>
Fresh in spring, yellowing and orange-red in autumn, dull in winter.

</td>
</tr>
<tr>
<td valign="top">

**❄️ Snow and ice that melt**<br>
Snow settles and melts, ponds and lakes freeze, and only what EZclimates placed ever goes.

</td>
<td valign="top">

**⛈️ The weather, planned**<br>
Fronts, thunderstorms, snowstorms, heat waves and cold snaps, planned days ahead so forecasts come true.

</td>
</tr>
<tr>
<td valign="top">

**🌱 Crops that grow by the air**<br>
Fields wait for spring, while a greenhouse EZvac keeps warm grows through winter.

</td>
<td valign="top">

**🌼 The ground through the year**<br>
Spring flowers and cherry petals, autumn leaf litter and mushrooms.

</td>
</tr>
<tr>
<td valign="top">

**🌡️ Body temperature**<br>
Cold and heat wear players down: rain soaks them, fires warm them, food helps.

</td>
<td valign="top">

**🥩 Food that spoils**<br>
Food keeps by the cold, much longer in EZvac's fridges and freezers.

</td>
</tr>
</table>

Switched as a whole or part by part from EZvac's Addons menu (`/hvac addons`), with no re-logging when the season turns.

## In detail

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
- **Food that spoils** (0.3.0; off until you switch it on). Food keeps by the cold: in an EZvac fridge or freezer it lasts much longer, a cold cellar or a snowy biome helps, and left out in summer it goes stale, spoils and rots. See [Food that spoils](#food-that-spoils).
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
| `/ezclimates weather end` | Ends what is going on now: rain, snow or a storm stops at once, and a heat wave or cold snap eases out over an in-game hour. The rest of the plan stays |
| `/ezclimates weather replan` | Plans the weather afresh from now |
| `/ezclimates here` | What EZclimates sees where you stand: what's falling, the air, the snow line, the water and its freezing point (`here <x> <z>` from the console) |
| `/ezclimates body reload` | Reads `body.yml` again |
| `/ezclimates body set <player> <°F>` | Sets a player's body temperature |
| `/ezclimates body sick <player> <mild\|moderate\|severe>`, `cure <player>`, `roll <player>` | Makes a player ill, cures them, or rolls their chance of falling ill now |
| `/ezclimates food` | The food in your hand: how fresh it is, and how fast it's going where you stand |
| `/ezclimates adopt [blocks]` | Takes on thin snow nobody recorded within that many blocks of you (64 by default), so it melts like EZclimates' own: for strays left by a server that was killed mid-snowfall. Only in biomes where Minecraft itself never snows. It also fixes grass still looking snowed on (gray top, white sides) after its snow melted |
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
| Food spoiling | Food keeps forever, and what it says stays as it was. This one starts off |

The choices are kept in EZvac's `config.yml` through restarts.

## Body temperature

This was the LiveExposure addon. It is off until you switch it on in `/hvac addons` (EZclimates, Body temperature), because by default it can kill (`lethal` in `body.yml`).

Besides the air, the body meets (0.2.0; each can be switched off in `body.yml`):
- **Getting wet:** rain soaks you in the open over a few minutes (snow slower, a thunderstorm faster), and water soaks you at once. Wet, the cold feels up to 12 °F colder and takes your heat twice as fast. You dry off out of the rain, faster in the warmth, by a fire and in the sun. The HUD shows how wet you are (☂45%), and soaked, water drips off you.
- **Fires:** campfires, fire, lava and magma warm you within 4 blocks, most right beside them: against the cold up to comfortable, so beside a campfire you're comfortable even at 0 °F, and they bring a chilled body back quickly (about two minutes from freezing beside a campfire). In the heat only a quarter of their warmth counts, which still makes it worse. Water and a cool drink likewise bring an overheated body down faster.
- **Darkness** closes in at the deadly stages, freezing and heatstroke.
- **Food:** stews and soups, cooked meat and fish, baked potatoes and pumpkin pie hold the cold off for a few minutes, as leather does; melon, berries and water bottles hold off the heat.
- **Metal armor in the sun:** each iron, chainmail, gold, copper, diamond or netherite piece adds 2.5 °F of heat in direct sun. Leather and no armor stay cool.
- **Working hard:** sprinting, swimming and mining warm you by up to 8 °F, which helps in the cold and hurts in a heat wave.
- **Breath:** a puff shows in front of your face in air below 40 °F.
- **Metabolism:** keeping warm burns food, so in the cold you get hungry up to 1.75 times as fast, and with 3 drumsticks or fewer the cold takes your heat 1.5 times as fast. Eat before a winter trip.
- **Sleeping cold:** a night asleep where you still feel cold (with everything you wear and ate counted) wakes you chilled and makes a cold likelier.

`/bodytemp` lists what is warming or chilling you right now.

- **Coming from LiveExposure:** remove its jar and leave its folder. On the next start EZclimates brings `plugins/LiveExposure/config.yml` over to `plugins/EZclimates/body.yml` and starts with body temperature on, and each player's body temperature and illness carry over as they join. While the LiveExposure jar is still installed, EZclimates leaves body temperature to it.
- **The sun and the shade** come from EZvac's feels-like now (BETA 1.0 Pilot 4), so LiveExposure's own `shade` setting is gone.
- **Kept as they were:** `/bodytemp`, the `liveexposure.use` and `liveexposure.bypass` permissions, and every other setting. `/liveexposure …` is `/ezclimates body …`.
- **On EZvac's HUD** (0.2.0) the section is two lines in EZvac's own style: `▍Body 98.6°F Normal`, then a gauge drawn like EZvac's feels-like one (coloured cold to hot, the marker at your temperature; EZvac BETA 1.0 Pilot 6 or newer, a plain bar before) and up to three icons: ✚ fever, ☂45% wet, ♨+60° by a fire, ☕3m a warm meal (❄ a cool one), ⚡1.4× hungry from the cold, ☀+10° hot armor, ⚒+5° working. EZvac's sidebar leaves addons two of its 15 lines. The section is `{section:ezclimates:body}`; a HUD format that named `{section:liveexposure:body}` needs that changed.
- LiveExposure 0.2.0 stays available for Minecraft 1.21.x servers, which EZclimates doesn't run on.

## Food that spoils

Switched on in `/hvac addons` (EZclimates, Food spoiling). It starts off, because spoiled food is gone.

- **Food keeps by the temperature where it's kept:** in your inventory by the air around you, in a chest or barrel by the air there, and in an [EZvac fridge or freezer](https://github.com/EZvac-Plugin/EZvac/blob/main/docs/fridges-and-freezers.md) by its inside (EZvac BETA 1.0 Pilot 7 or newer).
- **How fast:** at 70 °F raw meat and fish keep 4 Minecraft days, fruit and vegetables 8 to 12, bread 10. Every 18 °F warmer it goes twice as fast. A fridge keeps food 3 to 5 times longer, and frozen (28 °F and colder) about 10 to 20 times. Golden foods, honey, dried kelp, rotten flesh and spider eyes never spoil.
- **On the item:** `Fresh · 3 days`, `Stale · 1 day` or `Spoiled`.
  - Stale food feeds half as much.
  - Spoiled food feeds a quarter and gives you Hunger, with a chance of Nausea.
  - Half a life after it spoils, it turns into rotten flesh (meat and fish), an empty bowl (stews), an empty bucket (milk), or goes.
- **Food of about the same age kept in the same place still stacks.** Food is only looked at when someone could see it: in players' inventories every 30 seconds, and in a container when it's opened or shut. So nothing runs for food nobody looks at.

Each food's days, its fridge and freezer factors and what it turns into are in [`food.yml`](src/main/resources/food.yml), with `pace` to make all of them longer or shorter.

## Settings

See [`body.yml`](src/main/resources/body.yml) for body temperature, [`food.yml`](src/main/resources/food.yml) for food that spoils, and [`config.yml`](src/main/resources/config.yml) for the rest: plants (the temperatures crops grow between, flowers and mushrooms), rain and snow, the colour stages and how strongly each kind of biome turns, snow (radius, maximum layers, rate), ice, the weather (each season's dry spells, fronts and thunder, snowstorms, heat waves, cold snaps, and how sure forecasts are), and per-tick budgets. The plan is kept in `weather.yml`.

<details>
<summary><b>How it works</b></summary>

<br>

At start-up EZclimates reads the server's own vanilla biome files and writes a datapack of seasonal copies of the 52 surface overworld biomes: 5 stages × rain, snow or dry, 728 in all, named like `ezclimates:plains/autumn_late_snow` so F3 still says which biome it is. Each copy differs only in its temperature, precipitation and colours; spawns, sky, fog, music and water stay. Paper loads the pack, and packetevents swaps each chunk section's biomes for their copies in what each player is sent, by EZvac's rain-or-snow edge (a copy per 4×4 biome cell, so rain and snow meet along a wavy line on the land, never in layers above you) and the year's progress. Nothing of Mojang's ships in the jar.

The server still uses the real biomes, so a player in a desert thunderstorm sees rain but isn't wet by the server's count.

</details>

## Licence

MIT. EZclimates is released on GitHub only.
