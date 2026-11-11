# Basket Bounce 2D — Java 17

A playable Java Swing/Java2D arcade game with MongoDB-backed accounts and player statistics. Players can create an account, log in, or continue as a guest.

## Requirements
- JDK 17 or newer
- Maven 3.8+
- MongoDB Community Server running locally on `mongodb://localhost:27017`
- Internet connection for live Fish Game bonus puzzles (the core game works offline)

## Run in VS Code (Windows PowerShell)
1. Extract `BasketBounce.zip`.
2. Open the extracted `BasketBounce` folder in VS Code.
3. Install the **Extension Pack for Java** (Microsoft) and **Maven for Java** if VS Code prompts you.
4. Start the MongoDB service, then open Terminal → New Terminal and run:

```powershell
java -version
mvn -version
mvn clean compile
mvn exec:java
```

The first Maven run downloads Gson, the MongoDB Java driver, BCrypt and Maven plugins. The game window then opens. You can also use `mvn test` to run the included tests.

## Run in Eclipse
1. File → Import → Existing Maven Projects.
2. Select the extracted `BasketBounce` folder and finish.
3. Right-click `com.basketbounce.Main` → Run As → Java Application.

## Controls
- `A` / `←`: move left
- `D` / `→`: move right
- Keyboard movement uses Swing window-level key bindings, so it continues to work after clicking game screens.
- `ESC`: pause / resume
- Mouse: menu and screen actions

## Gameplay
- Start with 3 lives (maximum 3).
- Catch hearts (30), stars (50), coins (10), gifts (100), and cupcakes (75).
- Avoid bombs. Touching a bomb with the basket ends the run immediately. Missing a collectible costs one life.
- Complete each target score to advance through ten levels. A Level Complete screen appears with a Continue button before the next level.
- Level 1 remains the easiest level, with objects spawning about every 2.2 seconds and falling at about 80 pixels per second. Each new level gradually increases falling speed and spawn frequency, and slightly increases bomb chance.
- Each of the ten levels has its own weather environment: sunny, rainy, snowy, stormy, foggy, windy, night, autumn, desert and aurora. The basket stays near the bottom when the game window is resized or maximized, and the main menu stays centered.
- The official Fish Game API is requested every 15 seconds while playing. The client follows HTTP redirects, requests the documented `out=json&base64=no` format, reads the official `question` image URL plus the `fish` and `chest` answer values, and downloads the question image separately. It also accepts a Base64 data image and older single-solution response fields. The question is fetched asynchronously; the game does not block while waiting. Enter the fish value followed by the chest value (for example, `6,5`). A correct answer grants one life, or 150 points if already at full lives. An unavailable API never crashes the game and is reported in the HUD; no fake API question is substituted.

API: `http://marcconrad.com/uob/fish/api.php?out=json`
Documentation: `https://marcconrad.com/uob/fish/doc.php`

## MongoDB database

The game connects using the supplied local MongoDB URL:

`mongodb://localhost:27017`

The database name is `basketbounce`. MongoDB creates it automatically when the game starts and creates these collections:

- `users` — full name, username, email, BCrypt password hash, account creation time and last login time.
- `player_profiles` — each account's highest score, highest level, total games and total score.
- `game_runs` — completed game results linked to the account.

Usernames and email addresses are unique. Passwords are stored as BCrypt hashes, never as plain text. Login accepts either username or email. Database operations run outside the Swing UI thread so account actions do not freeze the game.

If MongoDB is not running, the game can still be opened and played as a guest, but Login and Create Account require the MongoDB service to be running. Guest statistics continue to be saved locally at:

`%USERPROFILE%\\.basketbounce\\guest-data.json`

Guest records remain local; signed-in account records are stored in MongoDB.

## Project structure
- `src/main/java/com/basketbounce/Main.java` — application entry point
- `ui/GameWindow.java` — window, screens, rendering, input and state transitions
- `game/` — basket, spawner, score, lives and level systems
- `model/` — game states, weather and falling objects
- `api/` — official API client and question model
- `storage/` — MongoDB authentication, account profiles and game-result persistence, plus local guest saves

## Notes / limitations
- MongoDB is used for account registration, login, player profiles and signed-in game results. Guest Mode remains available and uses local file persistence.
- Graphics are procedurally drawn with Java2D, so no external image/sprite pack is needed.
- A short synthesized three-note collection chime plays when a collectible is caught. The existing sound toggle turns it on or off; no external audio files are required.
- The Fish Game endpoint must be reachable for bonus questions. API response/image format changes or network outages are handled as a visible unavailable message; core gameplay remains playable.
