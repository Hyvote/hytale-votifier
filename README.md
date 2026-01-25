# 🗳️ HytaleVotifier

> Reward your players for supporting your server! 🎁

A Votifier-style plugin for Hytale that receives vote notifications from voting websites via HTTP and fires events for other plugins to handle rewards.

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🔐 **RSA Encryption** | 2048-bit RSA key pair generation with automatic first-run initialization |
| 🌐 **HTTP Endpoints** | REST API for receiving encrypted votes and checking server status |
| 📡 **Event System** | Fires `VoteEvent` via Hytale's event bus for other plugins to handle rewards |
| 🛡️ **Secure Protocol** | Compatible with standard Votifier encryption (RSA/ECB/PKCS1Padding) |
| 🎰 **Reward Commands** | Execute configurable server commands with random chance when votes are received |
| 📢 **Vote Broadcasting** | Announce votes to all online players with customizable messages |
| 🔔 **Toast Notifications** | Display in-game toast popups to voters using TaleMessage formatting |
| 🔄 **Update Checker** | Automatic GitHub release checking with admin notifications |
| 🧪 **Debug Tools** | Test endpoint and `/testvote` command for development and troubleshooting |

---

## 📋 Requirements

- ☕ **Java 25** or higher
- 🎮 **Hytale Server** with plugin support
- 🔌 **Nitrado:WebServer plugin** — Required for HTTP server

---

## 🚀 Installation

1. **Build the plugin** (if building from source):
   ```bash
   mvn clean package
   ```

2. **Copy the JAR** to your server's `mods/` directory:
   ```
   mods/
   └── HytaleVotifier-1.0.0.jar
   ```

3. **Ensure Nitrado:WebServer is installed** — HytaleVotifier depends on this plugin for HTTP handling

4. **Start the server** — RSA keys will be automatically generated on first run 🔑

5. **Verify installation** by accessing the status endpoint:
   ```
   GET http://your-server:port/Hyvote/HytaleVotifier/status
   ```

---

## ⚙️ Configuration

Configuration is stored in `config.json` in the plugin's data directory. The file is created automatically on first run with default values.

### 📁 Configuration File Location

```
mods/Hyvote_HytaleVotifier/
├── config.json       # Plugin configuration
└── keys/
    ├── public.key    # 📤 Share with voting sites
    └── private.key   # 🔒 Keep secure - never share!
```

### 📝 Full Configuration Example

```json
{
  "debug": false,
  "keyPath": "keys",
  "voteMessage": {
    "enabled": false,
    "titleMessage": "<orange>Vote Received!</orange>",
    "descriptionMessage": "<gray>Thanks for your vote on <orange>{from}</orange>!</gray>",
    "iconItem": "Ore_Gold"
  },
  "broadcast": {
    "enabled": false,
    "message": "<orange>{username}</orange> <gray>voted on</gray> <orange>{from}</orange><gray>!</gray>"
  },
  "rewardCommands": [
    {
      "enabled": false,
      "command": "give {username} Ingredient_Stick",
      "chance": 1.0
    },
    {
      "enabled": false,
      "command": "give {username} Ingredient_Bar_Iron",
      "chance": 0.1
    }
  ]
}
```

### Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `debug` | boolean | `false` | Enable verbose debug logging |
| `keyPath` | string | `"keys"` | Subdirectory for RSA keys (relative to plugin data directory) |
| `voteMessage` | object | — | Toast notification settings (see below) |
| `broadcast` | object | — | Server-wide broadcast settings (see below) |
| `rewardCommands` | array | — | Commands to execute on vote (see below) |

### 🔔 Vote Message (Toast Notifications)

Display a toast popup to the player who voted.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable toast notifications |
| `titleMessage` | string | `"<orange>Vote Received!</orange>"` | Toast title with TaleMessage formatting |
| `descriptionMessage` | string | `"<gray>Thanks for your vote on <orange>{from}</orange>!</gray>"` | Toast description with placeholders |
| `iconItem` | string | `"Ore_Gold"` | Item ID to display as the toast icon |

### 📢 Broadcast Settings

Announce votes to all online players.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable server-wide vote broadcasts |
| `message` | string | `"<orange>{username}</orange> <gray>voted on</gray> <orange>{from}</orange><gray>!</gray>"` | Broadcast message with TaleMessage formatting and placeholders |

### 🎰 Reward Commands

Execute server commands when votes are received. Each command in the array can have its own probability — perfect for tiered rewards!

| Option | Type | Description |
|--------|------|-------------|
| `enabled` | boolean | Whether this reward is active (allows disabling without removing) |
| `command` | string | Command to execute (without leading `/`). Supports placeholders. |
| `chance` | number | Probability of execution (0.0 to 1.0). Use `1.0` for guaranteed execution. |

**Example reward configuration:**
```json
"rewardCommands": [
  {
    "enabled": true,
    "command": "give {username} Ingredient_Stick 5",
    "chance": 1.0
  },
  {
    "enabled": true,
    "command": "give {username} Ingredient_Bar_Gold",
    "chance": 0.25
  },
  {
    "enabled": true,
    "command": "give {username} Ingredient_Diamond",
    "chance": 0.05
  }
]
```

☝️ In this example, every voter receives 5 sticks, has a 25% chance for a gold bar, and a 5% chance for a diamond. 💎

> ⚠️ **Security Note:** Usernames and service names are validated before command execution to prevent command injection. Only alphanumeric characters and underscores are allowed in usernames.

### 🏷️ Available Placeholders

The following placeholders can be used in messages and commands:

| Placeholder | Description |
|-------------|-------------|
| `{username}` | The in-game username of the player who voted |
| `{from}` | The name of the voting site (service name) |

### 🎨 TaleMessage Formatting

Vote messages and broadcasts support [TaleMessage](https://github.com/InsiderAnh/TaleMessage) formatting tags:

```
<red>Red text</red>
<orange>Orange text</orange>
<yellow>Yellow text</yellow>
<green>Green text</green>
<blue>Blue text</blue>
<gray>Gray text</gray>
<bold>Bold text</bold>
<italic>Italic text</italic>
<click:https://example.com>Clickable text</click>
```

---

## 🌐 HTTP API Reference

All endpoints are mounted at `/Hyvote/HytaleVotifier/`.

### GET /status

Health check endpoint that returns server status information.

**Response:**
```json
{
  "status": "ok",
  "version": "1.0.0",
  "serverType": "HytaleVotifier"
}
```

**Status Codes:**
- ✅ `200 OK` — Server is running and keys are initialized
- ❌ `503 Service Unavailable` — RSA keys not initialized

### POST /vote

Receives encrypted vote notifications from voting sites.

**Request Body:** Base64-encoded RSA-encrypted vote payload

**Response (Success):**
```json
{
  "status": "ok",
  "message": "Vote processed for PlayerName"
}
```

**Response (Error):**
```json
{
  "status": "error",
  "message": "Error description"
}
```

**Status Codes:**
- ✅ `200 OK` — Vote received and processed successfully
- ⚠️ `400 Bad Request` — Empty payload, invalid Base64, decryption failed, or invalid vote format
- ❌ `500 Internal Server Error` — Unexpected server error

### GET /test

Test endpoint for debugging vote flow without encryption. Fires a real `VoteEvent`.

**Query Parameters:**
| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `username` | Yes | — | Player username for test vote |
| `serviceName` | No | `TestService` | Voting site identifier |
| `address` | No | Request IP | Voter's IP address |

**Example:**
```
GET /Hyvote/HytaleVotifier/test?username=TestPlayer&serviceName=MyVoteSite
```

**Response:**
```json
{
  "status": "ok",
  "message": "Test vote fired for TestPlayer",
  "vote": {
    "serviceName": "MyVoteSite",
    "username": "TestPlayer",
    "address": "127.0.0.1",
    "timestamp": 1705267200000
  }
}
```

---

## 🔐 Vote Protocol

HytaleVotifier uses the standard Votifier protocol for secure vote transmission.

### 📄 Payload Format

The vote data is a newline-delimited string:
```
VOTE
serviceName
username
address
timestamp
```

### 🔒 Encryption Flow

```
1. 🌐 Voting site retrieves server's public key
2. 🔐 Voting site encrypts vote payload using RSA
3. 📦 Voting site Base64-encodes the encrypted bytes
4. 📤 Voting site POSTs the Base64 string to /vote endpoint
5. 📥 Server decodes Base64 and decrypts with private key
6. ✅ Server parses and validates vote data
7. 📡 Server fires VoteEvent for listening plugins
```

### Vote Record Fields

| Field | Type | Description |
|-------|------|-------------|
| `serviceName` | String | Identifier of the voting site (e.g., "TopHytaleSites") |
| `username` | String | In-game username of the player who voted |
| `address` | String | IP address of the voter (as reported by voting site) |
| `timestamp` | long | Epoch milliseconds when the vote was cast |

---

## 🔌 Plugin Integration

Want to build your own reward system? Other plugins can listen for vote events to handle rewards.

### Registering a Vote Listener

```java
import org.hyvote.plugins.votifier.event.VoteEvent;
import org.hyvote.plugins.votifier.vote.Vote;

public class MyRewardPlugin extends JavaPlugin {

    @Override
    protected void setup() {
        // Register vote event listener
        getEventRegistry().register(VoteEvent.class, this::onVote);
    }

    private void onVote(VoteEvent event) {
        Vote vote = event.getVote();

        String username = vote.username();
        String service = vote.serviceName();
        String address = vote.address();
        long timestamp = vote.timestamp();

        // Handle vote reward
        getLogger().info("Player " + username + " voted on " + service);

        // Example: Give reward to player
        // Player player = getServer().getPlayer(username);
        // if (player != null) {
        //     giveReward(player);
        // } else {
        //     // Store for later - player is offline
        //     storeOfflineReward(username);
        // }
    }
}
```

### Event Details

- 📦 **Event Class**: `org.hyvote.plugins.votifier.event.VoteEvent`
- ⚡ **Fires**: When a valid vote is received (encrypted or via test endpoint)
- 👤 **Offline Players**: Events fire regardless of player online status — your plugin should handle offline scenarios

### VoteEvent Convenience Methods

```java
event.getVote()        // Returns the Vote record
event.getServiceName() // Shortcut for vote.serviceName()
event.getUsername()    // Shortcut for vote.username()
event.getAddress()     // Shortcut for vote.address()
event.getTimestamp()   // Shortcut for vote.timestamp()
```

---

## 🔄 Update Checker

HytaleVotifier automatically checks for updates on GitHub when the server starts. Never miss a new feature! 🚀

### How It Works

1. 🚀 On server startup, the plugin queries the GitHub API for the latest release
2. 📋 If a newer version is available, a message is logged to the console
3. 👤 When players with admin permissions join, they receive a clickable notification with download links

### Console Output

When an update is available, the console displays:
```
[Votifier] A new update is available: v1.1.0
[Votifier] Download from CurseForge: https://www.curseforge.com/hytale/mods/votifier
[Votifier] Download from GitHub: https://github.com/Hyvote/hytale-votifier/releases/latest
```

### Player Notifications

Players with the appropriate permissions see a chat message with clickable links to download the update from CurseForge or GitHub.

---

## 🔑 Permissions

| Permission | Description |
|------------|-------------|
| `votifier.admin.testvote` | Use the `/testvote` command to fire test vote events |
| `votifier.admin` | Receive update notifications when joining the server |
| `votifier.admin.update_notifications` | Alternative permission for update notifications only |

---

## 🧪 Testing

### Using the Test Endpoint

```bash
# Fire a test vote via HTTP
curl "http://localhost:8080/Hyvote/HytaleVotifier/test?username=TestPlayer"

# With custom service name
curl "http://localhost:8080/Hyvote/HytaleVotifier/test?username=TestPlayer&serviceName=MyVoteSite"
```

### Using the /testvote Command

In-game command for testing vote events:

```
/testvote <username> [service]
```

| Argument | Required | Default | Description |
|----------|----------|---------|-------------|
| `username` | Yes | — | The player username for the test vote |
| `service` | No | `TestService` | The voting site name to simulate |

**Permission Required**: `votifier.admin.testvote`

This command fires a `VoteEvent` as if the specified player had voted, triggering all configured features (toast notifications, broadcasts, and reward commands). Perfect for testing your reward logic without external voting sites! 🎯

---

## 📄 License

MIT License

Copyright (c) 2026 Hyvote

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
