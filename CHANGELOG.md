# Changelog

## 0.3.1

- **A heat wave's or cold snap's days are counted right.** One started with
  `/ezclimates weather event heatwave` said "day 2 of 7" on its first day
  of five: its ease in, put in the past so it starts at full strength, was
  counted. Now it is day 1 the day it is started. And days are the world's
  days: one starting in the afternoon no longer says "day 1" two days
  running.

## 0.3.0

Food that spoils, and rain that lasts as long as Minecraft's. Runs on EZvac
BETA 1.0 Pilot 4 or newer; fridges and freezers need Pilot 7.

- **Food that spoils**, a new part in `/hvac addons` (off until switched
  on). Food keeps by the cold: in an EZvac fridge or freezer (Pilot 7) it
  lasts much longer, a cold cellar or a snowy biome helps, and left out it
  goes stale (feeds half as much), spoils (feeds a quarter, Hunger, maybe
  Nausea) and turns into rotten flesh, a bowl or a bucket. Each food's life
  is in the new `food.yml`; `/ezclimates food` shows the food in your hand.
  A mini fridge's chiller box keeps its slot colder than the rest, and an
  item's freshness line never replaces another plugin's line (EZvac's
  frosted over).
- Rain and snow last as long as Minecraft's own: every season's fronts
  are 12 to 24 in-game hours (summer's were as short as 2). A config.yml
  still on the old stock lengths moves to the new ones on start.
- Rain started by a plugin without a length (EssentialsX's `/weather
  storm`) lasts as long as Minecraft's own, 12 to 24 in-game hours, as
  vanilla does; it used to stop after an hour.

## 0.2.0

Body temperature meets more than the air, a HUD in EZvac's style, and
fixes for snow and for travelling fast. Runs on EZvac BETA 1.0 Pilot 4 or
newer; the HUD's gauge and EZvac's weather words matching the sky need
Pilot 6.

- **Body temperature meets more than the air.** Rain and snow soak you in
  the open and water at once, and being wet makes the cold colder (the HUD
  shows how wet, ☂45%); fires warm you near them; warm food holds the
  cold off and cool food the heat; metal armor bakes in the sun; sprinting,
  swimming and mining warm you; your breath shows in cold air; and a night
  asleep in the cold wakes you chilled. Each has its section in `body.yml`,
  added on upgrade, and `/bodytemp` lists what is warming or chilling you.
- Fires warm you up to comfortable against the cold (beside a campfire
  you're fine at 0 °F) and bring a chilled body back quickly: about two
  minutes from freezing instead of ten. Water or a cool drink brings an
  overheated one down faster. Darkness closes in at the deadly stages.
- Soaked players stream water (falling drops, a splash or two at their
  feet) until they're only wet.
- Metabolism: keeping warm burns food, so the cold makes you hungry up to
  1.75 times as fast, and a hungry body loses heat to the cold faster.
- The HUD section is in EZvac's own style: `▍Body 98.6°F Normal`, then a
  gauge like EZvac's feels-like one (EZvac BETA 1.0 Pilot 6) and icons
  with numbers: ☂45% wet, ♨+60° by a fire, ⚡1.4× hungry from the cold. Stock HUD lines are moved to the new
  ones on upgrade; lines a server wrote itself stay.
- Jumping the season by hand (`/hvac season set`) plans the weather afresh
  for the new season, so a summer heat wave no longer carries into winter.
- Grass, podzol and mycelium no longer stay gray-topped with white sides
  after EZclimates' snow melts off them: the melt tells the ground the snow
  is gone, and settling snow marks it snowed on, as vanilla does. Ground
  left that way by 0.1.0 heals by itself near players, or at once with
  `/ezclimates adopt`.
- No more lag spikes while travelling fast: working out rain and snow for
  newly reached chunks could take 100 ms in one tick. It now takes at most
  about 2 ms a tick, skips chunks that aren't loaded yet, and resends
  chunks without the extra copying. `/ezclimates status` shows the slowest
  tick of the last minute.
- Torches, lamps and other lights melt EZclimates' snow and ice as they
  do vanilla's, and snow never settles (nor water ices) by a light, as in
  vanilla. What the snow buried grows back when a light melts it.
- Tells EZvac (API 1.11) where it shows EZvac's rain and snow, so EZvac's
  HUD, sensors and forecasts say the same as the sky.

## 0.1.0

The first release: seasons you can see, from EZvac's climate.

- Rain and snow where EZvac's air says, by season, height and time of day.
- Leaf and grass colours in five stages through the year.
- Snow that settles and melts, and ice on cold water (ponds to oceans,
  unheated pools), recorded in each chunk so it always melts.
- The world keeps its own biomes: only what players are sent changes, and
  chunks players have are resent when the look changes.
- The weather planned days ahead and Minecraft's driven to match: fronts by
  season, thunderstorm fronts, winter snowstorms (heavy snow, a gale, a dark
  sky, no lightning), heat waves and cold snaps. EZvac's stations forecast
  from the plan, sure for today and tomorrow and less so further out.
- Plants: crops, stems, cocoa, sugar cane and saplings grow by EZvac's air
  where they stand, so a heated greenhouse grows through winter; flowers
  (with wildflowers) and pink petals under cherry trees come up in spring,
  and leaf litter under broadleaf trees and mushrooms in autumn, recorded like snow
  so only EZclimates' own ever go.
- Body temperature, as a part that is off until switched on: LiveExposure
  is part of EZclimates now. Its settings are in `body.yml`; a server that
  had LiveExposure gets its `config.yml` and its players' body temperatures
  brought over. Its own shade cooling is gone, since EZvac's feels-like
  counts the sun and the shade. `/bodytemp` stays, `/liveexposure` is
  `/ezclimates body`, and the HUD section is `{section:ezclimates:body}`.
- Switched from EZvac's Addons menu (`/hvac addons`), all of it or a part at
  a time: seasonal looks, snow and ice, planned weather, plants, body
  temperature.
- `/ezclimates status`, `here`, `weather`, `body`, `clear` and `resume`.
- Needs Paper or Purpur 26.1+, EZvac BETA 1.0 Pilot 4 (API 1.9) and
  packetevents 2.14.0.
