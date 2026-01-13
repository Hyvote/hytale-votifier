# HytaleVotifier

## What This Is

A Votifier-style plugin for Hytale that receives vote notifications from voting websites via HTTP and fires events for other plugins to handle rewards. Interfaces with the Nitrado:WebServer plugin infrastructure and targets the broader Hytale server community.

## Core Value

Clean, secure vote event API — other plugins can reliably listen to vote events with confidence that the votes are authentic (RSA-encrypted from trusted voting sites).

## Current State

**Version:** v1.0 MVP (shipped 2026-01-14)
**Codebase:** 1,060 lines Java (13 files)
**Tech Stack:** Maven, Java 25, Hytale Server API, Nitrado WebServer, Jakarta Servlet

**What's Working:**
- 2048-bit RSA key pair generation with automatic first-run initialization
- HTTP POST /vote endpoint accepting encrypted vote payloads
- HTTP GET /status endpoint returning server info and Base64 public key
- Vote decryption and Votifier protocol parsing
- VoteEvent firing via Hytale event system
- Test endpoint and /testvote command for debugging

## Requirements

### Validated

- [x] 2048-bit RSA key pair generation on first run — v1.0
- [x] Keys stored in plugin config directory for admin access — v1.0
- [x] HTTP POST endpoint to receive encrypted votes — v1.0
- [x] HTTP GET endpoint for status/health check — v1.0
- [x] Decrypt votes using private key — v1.0
- [x] Parse standard Votifier fields (serviceName, username, address, timestamp) — v1.0
- [x] Fire vote events via Hytale's event system — v1.0
- [x] Events fire regardless of player online status — v1.0
- [x] Test HTTP endpoint for debugging — v1.0
- [x] In-game /testvote command with custom permission node (hyvote.testvote) — v1.0
- [x] Log and reject failed vote attempts with appropriate HTTP status codes — v1.0
- [x] JSON configuration file — v1.0 (defaults only, file loading deferred)

### Active

(None — v1.0 complete)

### Out of Scope

- Vote forwarding/proxy support — keep v1 simple, single-server only
- Vote storage/history database — listening plugins handle persistence
- Built-in reward system — fire events only, let other plugins handle rewards
- Legacy Votifier socket protocol — HTTP-only via webserver plugin

## Context

**Reference Projects:**
- NuVotifier (https://github.com/NuVotifier/NuVotifier) — Votifier protocol and RSA security model
- Nitrado WebServer Plugin (https://github.com/nitrado/hytale-plugin-webserver) — HTTP infrastructure, servlet registration
- Nitrado Query Plugin (https://github.com/nitrado/hytale-plugin-query) — Project structure and webserver integration patterns

**Technical Environment:**
- Hytale game server plugin ecosystem
- Depends on Nitrado:WebServer plugin for HTTP handling
- Servlet registration via `webServerPlugin.addServlet(plugin, path, servlet)`
- Endpoints mounted at `/Hyvote/HytaleVotifier/<path>`
- Uses `jakarta.servlet.http` classes

**Vote Flow:**
1. Voting site encrypts vote data with server's public key
2. Voting site POSTs encrypted payload to server endpoint
3. Plugin decrypts with private key
4. Plugin validates vote data
5. Plugin fires VoteEvent via Hytale event system
6. Listening plugins handle rewards/notifications

## Constraints

- **Build System**: Maven — consistent with query plugin reference
- **Java Version**: Java 25 — required target version
- **License**: MIT — permissive open source
- **Dependency**: Nitrado:WebServer plugin — required for HTTP infrastructure
- **Security**: 2048-bit RSA — standard Votifier security model

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| HTTP-only, no socket protocol | Simpler implementation, leverages existing webserver infrastructure | Good |
| Fire events for offline players | Keeps plugin simple, listening plugins decide how to handle | Good |
| RSA key pair authentication | Matches Votifier security model, trusted by voting sites | Good |
| JSON config format | Consistent with query plugin patterns | Good |
| Custom permission node for /testvote | Granular access control for testing features | Good |
| Java records for Vote/Config | Immutability, conciseness, native Java 25 | Good |
| Graceful degradation for WebServer | Plugin continues without HTTP if WebServer absent | Good |
| RSA/ECB/PKCS1Padding cipher | Votifier protocol compatibility | Good |

---
*Last updated: 2026-01-14 after v1.1 milestone*
