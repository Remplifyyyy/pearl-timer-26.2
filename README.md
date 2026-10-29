# Remplify Pearl Timer (Fabric 26.2)

Client-side mod. When another player throws an ender pearl, a countdown appears above their name tag
(`Pearl 15s` → `Pearl 1s`) and disappears when their cooldown is over. Your own pearls are ignored.

Needs Fabric Loader 0.20.1+ and Fabric API for 26.2.

## Cooldown length (per server)
- **Auto-detect (on by default):** throw one pearl yourself and the mod reads the cooldown the server puts on
  your pearl (the grey sweep over the pearl in your hotbar). It's saved for that server address, so you only
  do this once per server. A chat message confirms the value it learned.
- Until it's learned, players who throw a pearl get `Pearl ?` above their head for a few seconds instead of a guessed time.
- **Manual:** `/pearltimer cooldown <seconds>` sets it for the server you're on.
- `/pearltimer reset` forgets this server's value so it re-detects on your next throw.

## Keybind
**Toggle Pearl Timers** is in Options → Controls → Key Binds → *Pearl Timer*. It's unbound by default;
set any key you like. Pressing it shows "Pearl timers ON/OFF" above your hotbar.

## Commands
| Command | What it does |
|---|---|
| `/pearltimer` | Shows current settings |
| `/pearltimer toggle` | Turns timers on/off |
| `/pearltimer cooldown <seconds>` | Sets the cooldown length for this server |
| `/pearltimer reset` | Forgets this server's cooldown and re-detects it |
| `/pearltimer autodetect <true/false>` | Turns auto-detect on/off |
| `/pearltimer decimals <true/false>` | Shows `12.4s` instead of `13s` |
| `/pearltimer clear` | Clears all active timers |

Settings are stored in `config/pearltimer.json`.

## Building
Push this folder to a GitHub repo — the included GitHub Actions workflow builds it automatically.
Download the jar from the run's **Artifacts** (use the one **without** `-sources` in the name)
and put it in `.minecraft/mods`.

Or build locally with Java 25+: `./gradlew build` → `build/libs/pearltimer-1.0.0.jar`.
