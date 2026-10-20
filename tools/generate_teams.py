#!/usr/bin/env python3
"""Generates app/src/main/assets/teams.json from ESPN's public teams endpoints.

Run from the repo root:  python tools/generate_teams.py
Python 3 stdlib only. The output is committed; the app never fetches it at runtime.
Re-run when a team is renamed or relocated, and add quirks to MANUAL_ALIASES below.
"""
import json
import pathlib
import sys
import urllib.request

LEAGUES = [  # (our league, ESPN sport path, ESPN league path)
    ("NFL", "football", "nfl"),
    ("MLB", "baseball", "mlb"),
    ("NHL", "hockey", "nhl"),
    ("NBA", "basketball", "nba"),
]
URL = "https://site.api.espn.com/apis/site/v2/sports/{sport}/{league}/teams"

# Extra full names a playlist may use, keyed by (league, ESPN displayName). Never add a bare
# nickname that exists in another league (Giants, Cardinals, Rangers, Kings, Panthers, Jets...).
MANUAL_ALIASES = {
    ("MLB", "Athletics"): ["Oakland Athletics", "Las Vegas Athletics", "Sacramento Athletics", "A's", "Oakland A's"],
    ("MLB", "St. Louis Cardinals"): ["Saint Louis Cardinals"],
    ("MLB", "Los Angeles Angels"): ["LA Angels", "Anaheim Angels", "Los Angeles Angels of Anaheim"],
    ("MLB", "Los Angeles Dodgers"): ["LA Dodgers"],
    ("MLB", "Arizona Diamondbacks"): ["Arizona D-backs", "Arizona Dbacks"],
    ("MLB", "Chicago White Sox"): ["Chicago Whitesox"],
    ("NHL", "Utah Mammoth"): ["Utah Hockey Club", "Utah HC"],
    ("NHL", "St. Louis Blues"): ["Saint Louis Blues"],
    ("NHL", "Montreal Canadiens"): ["Montréal Canadiens"],
    ("NHL", "Vegas Golden Knights"): ["Las Vegas Golden Knights"],
    ("NHL", "Los Angeles Kings"): ["LA Kings"],
    ("NBA", "LA Clippers"): ["Los Angeles Clippers"],
    ("NBA", "Los Angeles Lakers"): ["LA Lakers"],
    ("NBA", "Portland Trail Blazers"): ["Portland Trailblazers"],
    ("NBA", "Philadelphia 76ers"): ["Philadelphia Sixers"],
    ("NFL", "Los Angeles Rams"): ["LA Rams"],
    ("NFL", "Los Angeles Chargers"): ["LA Chargers"],
    ("NFL", "Washington Commanders"): ["Washington Football Team"],
}


def fetch(sport, league):
    req = urllib.request.Request(URL.format(sport=sport, league=league), headers={"User-Agent": "uzzu-teams-generator"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.load(resp)
    return [t["team"] for t in data["sports"][0]["leagues"][0]["teams"]]


def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    out = root / "app" / "src" / "main" / "assets" / "teams.json"
    teams, used = [], set()
    for ours, sport, league in LEAGUES:
        fetched = fetch(sport, league)
        if not fetched:
            sys.exit(f"no teams returned for {ours}")
        for t in sorted(fetched, key=lambda t: t["displayName"]):
            key = (ours, t["displayName"])
            aliases = MANUAL_ALIASES.get(key, [])
            if aliases:
                used.add(key)
            teams.append({
                "league": ours,
                "displayName": t["displayName"],
                "location": t.get("location", ""),
                "name": t.get("name", ""),
                "shortDisplayName": t.get("shortDisplayName", ""),
                "abbreviation": t.get("abbreviation", ""),
                "aliases": aliases,
            })
        print(f"{ours}: {len(fetched)} teams")
    for key in MANUAL_ALIASES.keys() - used:
        print(f"WARNING: manual alias target not found: {key}", file=sys.stderr)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"source": "ESPN site API teams endpoints (tools/generate_teams.py)", "teams": teams},
                              ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {len(teams)} teams to {out.relative_to(root)}")


if __name__ == "__main__":
    main()
