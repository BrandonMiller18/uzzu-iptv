# ESPN scoreboard fixtures

`{league}_{yyyyMMdd}.json` are unmodified responses from ESPN's public scoreboard API
(`https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/scoreboard?dates=yyyyMMdd`),
recorded on 2026-10-20. The six games in `../sample.m3u` are "post" events on 20261019; the
20261020 files hold "pre" events.

There was no in-progress game to record, so the `*_20261019_in.json` files are **synthetic**:
copies of the real 20261019 recordings in which only the status fields of one event
(`status.type` and `competitions[0].status.type`: id, name, state "in", completed, description,
detail, shortDetail) were edited:

- `nhl_20261019_in.json`: Anaheim Ducks @ New York Rangers → "2nd 8:14"
- `mlb_20261019_in.json`: Cleveland Guardians @ Tampa Bay Rays → "Top 5th"
- `nfl_20261019_in.json`: Washington Commanders @ San Francisco 49ers → "Q3 4:12"

The responses contain score fields (real ESPN data); the app never decodes them.
