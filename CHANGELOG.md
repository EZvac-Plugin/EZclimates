# Changelog

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
- Body temperature, as a part that is off until switched on: LiveExposure
  is part of EZclimates now. Its settings are in `body.yml`; a server that
  had LiveExposure gets its `config.yml` and its players' body temperatures
  brought over. Its own shade cooling is gone, since EZvac's feels-like
  counts the sun and the shade. `/bodytemp` stays, `/liveexposure` is
  `/ezclimates body`, and the HUD section is `{section:ezclimates:body}`.
- Switched from EZvac's Addons menu (`/hvac addons`), all of it or a part at
  a time: seasonal looks, snow and ice, planned weather, body temperature.
- `/ezclimates status`, `here`, `weather`, `body`, `clear` and `resume`.
- Needs Paper or Purpur 26.1+, EZvac BETA 1.0 Pilot 4 (API 1.9) and
  packetevents 2.14.0.
