# Ginebra Online - Implementation Progress

This document tracks what has been implemented.

---

## Phase 1: Identity Foundation
**Status: COMPLETE**

| Step | Description | Status |
|------|-------------|--------|
| 1 | POST /api/auth/anonymous endpoint | Done |
| 2 | GET /api/auth/me endpoint | Done |
| 3 | JWT validation filter | Done |
| 4 | In-memory token store (for anonymous session tracking) | Done |

---

## Phase 2: Lobby - Room Management
**Status: COMPLETE**

| Step | Description | Status |
|------|-------------|--------|
| 1 | Room domain model | Done |
| 2 | POST /api/rooms (create room) | Done |
| 3 | GET /api/rooms (list joinable rooms) | Done |
| 4 | POST /api/rooms/{id}/join | Done |
| 5 | POST /api/rooms/{id}/leave | Done |
| - | In-memory room repository | Done |
| - | Room lifecycle (WAITING → STARTING) | Done |

---

## Phase 3: Game Engine - Core Domain
**Status: COMPLETE**

| Step | Description | Status |
|------|-------------|--------|
| 1 | Card, Suit, Rank (value objects) | Done |
| 2 | Deck | Done |
| 3 | CardRankingService (all 4 trump scenarios) | Done |
| 4 | Basa, PlayedCard | Done |
| 5 | Round | Done |
| 6 | Game (aggregate root) | Done |
| 7 | MoveValidator, BasaResolver, TeamResolver | Done |

---

## Phase 4: WebSocket Integration
**Status: NOT STARTED**

| Step | Description | Status        |
|------|-------------|---------------|
| - | WebSocket configuration with STOMP | DONE          |
| - | /ws/game/{gameId} endpoint | DONE          |
| - | Message types (client→server, server→client) | DONE          |
| - | Connection tracking | DONE          |
| - | Game event broadcasting | IN PROGRESSgh<br/> |
| - | Room → Game transition trigger | Pending       |
| - | : End-to-end integration test | Pending       |


---

## Phase 5: Persistence & Recovery
**Status: NOT STARTED**

| Step | Description | Status |
|------|-------------|--------|
| - | PostgreSQL schema | Pending |
| - | JdbcTemplate repositories | Pending |
| - | Event logging | Pending |
| - | State snapshot updates | Pending |
| - | Server restart recovery | Pending |
| - | Player reconnection with state sync | Pending |

---

## Phase 6: Hardening
**Status: NOT STARTED**

| Step | Description | Status |
|------|-------------|--------|
| - | Rate limiting | Pending |
| - | Graceful error responses | Pending |
| - | Timeout handling | Pending |
| - | Game abandonment detection | Pending |

---

## Commit History

| Commit | Description |
|--------|-------------|
| bd2a9ad | Phase 2 - [AI] /post for creating the room |
| 64ae76e | Phase 2 - [AI] Room domain model |
| 1b9d204 | Phase 1 - [AI] JWT filter |
| 86a64a4 | Phase 1 - [AI] /api/auth/me get anon user endpoint |
| 17e77ec | Phase 1 - [AI] /api/auth create anon session |
