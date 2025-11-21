# CS209A Assignment 2 Demo

## Environment

**java JDK**: openjdk-22 (Oracle OpenJDK 22.0.2)

**javafx-fxml**: 22.0.1

**javafx-controls**: 22.0.1

**maven**: 3.8.5

## Architecture

This project is a small client–server "QQ Farm" style game implemented with JavaFX on the client side and a plain TCP server on the backend.

### Modules and packages

- `org.example.demo` — JavaFX front end
  - `Application` — JavaFX entry point; creates the stage and loads `board.fxml`.
  - `Controller` — UI controller; manages the 4×4 farm grid, buttons, and interaction with the network layer.
- `org.example.demo.client`
  - `ClientNetworkService` — manages the TCP socket to the farm server, background receive loop, and reconnection.
- `org.example.demo.server`
  - `FarmServer` — multi-threaded server; accepts clients and assigns each a `ClientHandler`.
  - `ClientHandler` — per-connection handler; parses protocol commands from a single client and updates the corresponding `PlayerState`.
  - `PlayerState` — holds one player's farm, coins, and active connection.
  - `PlotState` — represents a single farm cell state on the server.

### High-level data flow

1. **UI → Controller**: User clicks buttons (Plant, Harvest, Steal, Visit, Return, Disconnect, Reconnect).
2. **Controller → ClientNetworkService**: Controller validates the action, then sends a line-based command (e.g. `PLANT 1 2`) over TCP.
3. **ClientNetworkService → FarmServer**: The message is written to the socket; on the server a `ClientHandler` receives it.
4. **FarmServer / ClientHandler**: Parses the command, updates `PlayerState` / `PlotState`, and computes responses.
5. **FarmServer → ClientNetworkService**: Server pushes responses such as `SUCCESS ...` or `FARM ...` to the client.
6. **ClientNetworkService → Controller**: A background listener thread reads each line and dispatches it to the `Controller` via `messageHandler` on the JavaFX UI thread.
7. **Controller → UI**: Controller updates internal model (`farmState`, coins, current player view) and refreshes the JavaFX board.

### Connection management

- On startup, the client connects to `localhost:8888` via `ClientNetworkService.connect` and shows a login dialog.
- If the socket is closed unexpectedly, `ClientNetworkService` invokes a `connectionLostHandler`:
  - The `Controller` disables all action buttons and shows a "Disconnected" message.
  - A **Reconnect** button becomes visible.
- When the user clicks **Reconnect**:
  - The client calls `ClientNetworkService.reconnect` to create a new socket.
  - The controller automatically re-sends the last successful username (if available) to restore farm state.

## File List

- `Application.java`: the main entry point of the demo application.
- `Game.java`: manages the game logic and controls the game's behavior (single-player reference).
- `Controller.java`: handles JavaFX UI interactions and events; bridges UI and network.
- `ClientNetworkService.java`: handles TCP connection, sending, receiving, and reconnect logic.
- `FarmServer.java`, `ClientHandler.java`, `PlayerState.java`, `PlotState.java`: server-side logic and persistent player farm states.
- `board.fxml`: JavaFX layout for the game board and action buttons.
- `styles.css`: JavaFX CSS for buttons, plots, and status bar.
- `resources`: stores pictures for the game board (https://www.iconfont.cn/).

## Text Protocol

The client and server use a simple, line-based text protocol over TCP. Each message is a single line terminated by `\n`, with tokens separated by spaces.

### Client → Server commands

- `LOGIN <username>`
  - Sent after the user enters a name.
  - If the username has existing server-side state, the game continues from that state (supports reconnect).

- `PLANT <row> <col>`
  - Plant a crop at the given 0-based `(row, col)` if the plot is empty and the player has enough coins.

- `HARVEST <row> <col>`
  - Harvest a ripe crop at `(row, col)`. The server updates coins and plot state.

- `STEAL <targetPlayer>`
  - Visit another player's farm to attempt stealing from ripe plots.

- `VISIT <friendName>`
  - View another player's farm without stealing.

- `RETURN`
  - Return from viewing someone else's farm back to the current player's own farm.

- `PING`
  - Optional heartbeat. The server may respond with `PONG`.

### Server → Client messages

- `WELCOME ...`
  - Sent once when the connection is established.

- `LOGIN SUCCESS` / `LOGIN ERROR <reason>`
  - Indicates login result. On success, the controller enables action buttons.

- `SUCCESS <message>`
  - Generic success message for an operation (plant/harvest/steal/visit/return).

- `ERROR <message>`
  - Generic error description when an operation fails (invalid plot, not enough coins, etc.).

- `FARM <username> <coins> <cell1> <cell2> ... <cell16>`
  - Full snapshot of a player's farm, always 16 cells for a 4×4 grid.
  - Each cell is formatted as `<STATE>[:yield]`, for example:
    - `EMPTY`
    - `GROWING:0`
    - `RIPE:3`
  - The client parses this into its local `farmState` and updates the board.

- `NOTIFY <message>`
  - Informational broadcast or notification to the client.

- `PONG`
  - Response to `PING` (if implemented).

The protocol is intentionally simple and human-readable to ease debugging via tools like `telnet` or `nc`.

## How to Run

### 1. Start the server

From the project root:

```bash
mvn -q -DskipTests package
java -cp target/classes org.example.demo.server.FarmServer
```

This starts a TCP server on port `8888` and prints connection logs to the console.

### 2. Start the client (JavaFX UI)

In a second terminal, run from the project root:

```bash
mvn -q javafx:run
```

Or, if you prefer to run the `Application` class directly, make sure the JavaFX modules are on the module path according to your local setup.

### 3. Basic usage flow

1. Launch the server.
2. Launch the client.
3. When prompted, enter a username (e.g., `player1`).
4. Select a cell in the 4×4 grid and click **Plant**.
5. After the crop grows and becomes ripe, click **Harvest** to earn coins.
6. Use **Visit** / **Return** to view other players' farms.
7. Use **Disconnect** to simulate network failure and **Reconnect** to restore the previous session.

## Logic

- **Game start**: prompt for username, connect to server, and initialize a 4×4 farm board.
- **Operations validity**: the controller checks selection, login status, and connection status before sending commands; the server validates operations against farm rules.
- **Game finish / feedback**: while there is no fixed "end" condition, the status bar always shows toasts for success/error/info events.

## Notes

- It is recommended to first complete or understand the single-player mode (`Game.java`). Once comfortable, you can extend or refactor into richer multi-player behavior.
- If you encounter GUI issues when rendering multiple game boards or stages, check the `start` method in `Application` and the `board.fxml` layout.
- For questions or bug reports, contact: `12442018@mail.sustech.edu.cn` or QQ: `503652093`.
