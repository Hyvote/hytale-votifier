# HytaleVotifier

A Votifier-style plugin for Hytale that receives vote notifications from voting websites via HTTP and fires events for other plugins to handle rewards.

## Features

- **RSA Encryption**: 2048-bit RSA key pair generation with automatic first-run initialization
- **HTTP Endpoints**: REST API for receiving encrypted votes and checking server status
- **Event System**: Fires `VoteEvent` via Hytale's event bus for other plugins to handle rewards
- **Secure Protocol**: Compatible with standard Votifier encryption (RSA/ECB/PKCS1Padding)
- **Debug Tools**: Test endpoint and `/testvote` command for development and troubleshooting

## Requirements

- **Java 25** or higher
- **Hytale Server** with plugin support
- **Nitrado:WebServer plugin** - Required for HTTP server

## Installation

1. **Build the plugin** (if building from source):
   ```bash
   mvn clean package
   ```

2. **Copy the JAR** to your server's `plugins/` directory:
   ```
   plugins/
   └── HytaleVotifier-1.0.0.jar
   ```

3. **Ensure Nitrado:WebServer is installed** - HytaleVotifier depends on this plugin for HTTP handling

4. **Start the server** - RSA keys will be automatically generated on first run

5. **Verify installation** by accessing the status endpoint:
   ```
   GET http://your-server:port/Hyvote/HytaleVotifier/status
   ```

## Configuration

### Key Storage

RSA keys are stored in the plugin's data directory:
```
plugins/Hyvote-HytaleVotifier/
└── keys/
    ├── public.key    # Share with voting sites
    └── private.key   # Keep secure - never share
```

### Default Settings

- **Key Path**: `keys` (relative to plugin data directory)
- **Debug Mode**: Disabled by default

> **Note**: JSON configuration file loading is planned for a future version. Currently uses sensible defaults.

## HTTP API Reference

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
- `200 OK` - Server is running and keys are initialized
- `503 Service Unavailable` - RSA keys not initialized

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
- `200 OK` - Vote received and processed successfully
- `400 Bad Request` - Empty payload, invalid Base64, decryption failed, or invalid vote format
- `500 Internal Server Error` - Unexpected server error

### GET /test

Test endpoint for debugging vote flow without encryption. Fires a real `VoteEvent`.

**Query Parameters:**
| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `username` | Yes | - | Player username for test vote |
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

## Vote Protocol

HytaleVotifier uses the standard Votifier protocol for secure vote transmission.

### Payload Format

The vote data is a newline-delimited string:
```
VOTE
serviceName
username
address
timestamp
```

### Encryption

1. **Voting site** retrieves server's public key (from `/status` or manual configuration)
2. **Voting site** encrypts vote payload using RSA with the public key
3. **Voting site** Base64-encodes the encrypted bytes
4. **Voting site** POSTs the Base64 string to `/vote` endpoint
5. **Server** decodes Base64 and decrypts with private key
6. **Server** parses and validates vote data
7. **Server** fires `VoteEvent` for listening plugins

### Vote Record Fields

| Field | Type | Description |
|-------|------|-------------|
| `serviceName` | String | Identifier of the voting site (e.g., "TopHytaleSites") |
| `username` | String | In-game username of the player who voted |
| `address` | String | IP address of the voter (as reported by voting site) |
| `timestamp` | long | Epoch milliseconds when the vote was cast |

## Plugin Integration

Other plugins can listen for vote events to handle rewards.

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

- **Event Class**: `org.hyvote.plugins.votifier.event.VoteEvent`
- **Fires**: When a valid vote is received (encrypted or via test endpoint)
- **Offline Players**: Events fire regardless of player online status - your plugin should handle offline scenarios

### VoteEvent Convenience Methods

```java
event.getVote()        // Returns the Vote record
event.getServiceName() // Shortcut for vote.serviceName()
event.getUsername()    // Shortcut for vote.username()
event.getAddress()     // Shortcut for vote.address()
event.getTimestamp()   // Shortcut for vote.timestamp()
```

## Testing

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
/testvote <username>
```

**Permission Required**: `hyvote.testvote`

This command fires a `VoteEvent` as if the specified player had voted, allowing you to test your reward logic without external voting sites.

## License

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
