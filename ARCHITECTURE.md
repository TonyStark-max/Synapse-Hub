# Technical Architecture: Synapse Hub

Synapse Hub is a multi-tenant idea management platform designed for internal organizations. The central engineering challenge of this system is guaranteeing absolute tenant data isolation—ensuring no workspace can ever inspect, modify, or interact with data belonging to another workspace—while simultaneously maintaining real-time, gravity-ranked activity feeds for collaborative users.

---

## Goals and Non-Goals

### Goals
*   **Absolute Tenant Isolation:** Prevent cross-tenant data leaks (such as IDOR attacks) at both the application and database layers.
*   **Deterministic Hot Ranking:** Ensure the idea feed ranks new and highly upvoted content accurately using log-decay time gravity.
*   **Real-time Event Synchronization:** Deliver live state updates (new ideas, votes, comments) to all users active in the same workspace.
*   **Zero-Dependency Local Testing:** Guarantee that any recruiter or developer can run and test all functional roles locally without requiring external SaaS credentials.

### Non-Goals
*   **Cross-Tenant Analytics:** The system deliberately prevents aggregation or search across different workspaces.
*   **High-Volume Real-Time Chat:** The real-time layer is scoped exclusively to feedback events, not instant messaging.
*   **User Identity Management:** User registration, profile hosting, and session verification are delegated to Clerk (in production) to focus strictly on tenant workspace logic.

---

## System Overview

At rest, Synapse Hub models a workspace topology around five key entities:
*   **Organization (Tenant):** Represents an isolated workspace directory with a unique ID and invitation code.
*   **User:** Represents an employee account, mapped to a single active workspace (`orgId`) and role (`role`).
*   **Idea:** A feedback submission belonging to a workspace, posted by a user.
*   **Vote:** An upvote or downvote cast on an idea by a user (restricted to one vote per user per idea).
*   **Comment:** A threaded comment or reply posted in response to an idea.

```
┌────────────────────────────────────────────────────────┐
│                      Organization                      │
└───────┬────────────────────────────────────────┬───────┘
        │ 1:N                                    │ 1:N
┌───────▼───────┐                        ┌───────▼───────┐
│     User      │                        │     Idea      │
└───────┬───────┘                        └───────┬───────┘
        │ 1:N                                    │ 1:N
        ├───────────────────┬────────────────────┤
        │ 1:1               │                    │ 1:N
┌───────▼───────┐           │            ┌───────▼───────┐
│     Vote      │           └───────────>│    Comment    │
└───────────────┘                        └───────────────┘
```

---

## Core Components

### Auth & Identity (Clerk / Local Sandbox)
Authenticates user sessions. In production, Clerk verifies user claims via signed JWTs. In development, a mock sandbox router creates local unsigned JWT tokens, enabling immediate login for testers.

### API Layer (Spring Boot 3)
A stateless REST API built using Java 21 and Spring Boot. It intercepts incoming calls, extracts JWT claims, runs the token-bucket rate limiter, and executes business logic.

### Data Layer (PostgreSQL 16)
Stores relational tables. PostgreSQL Row-Level Security (RLS) is enabled on workspace tables (`ideas`, `comments`, `votes`) to enforce tenant isolation rules independently of the Java application query clauses.

### Real-Time Layer (Supabase Realtime)
Broadcasts database mutation events (inserts, updates, deletes) to the client. The real-time listener is governed by the same RLS database policies, preventing event leakages across workspaces.

### Frontend (React 18)
A TypeScript Single Page Application (SPA) compiled with Vite. Renders a responsive dashboard, handles client-side state, and communicates with the REST API using Axios/Fetch.

---

## Data Flow: Submitting and Viewing an Idea

1.  **Client Request:** The React client sends a `POST /api/ideas` request. The header contains a bearer token: `Authorization: Bearer <JWT>`.
2.  **Authentication & Profile Resolution:** The backend `TenantContextFilter` intercepts the request, decodes the JWT claims, and extracts the authenticated user ID (`sub`).
3.  **Database Lookup:** The filter queries the `users` table directly. Since RLS is disabled on `users` (as it acts as a global directory), the lookup resolves the user's active `org_id` and `role`.
4.  **Binding Context:** The filter stores the resolved `org_id` and `role` in the thread-local `TenantContext` block.
5.  **Transaction Initialization:** The service layer starts a `@Transactional` block. The Spring `TransactionManager` interceptor retrieves the thread's context and executes session variable bindings:
    ```sql
    SELECT set_config('app.current_org_id', 'org_cricket', true);
    SELECT set_config('app.current_user_id', 'user_123', true);
    ```
6.  **Database Write:** The JPA repository executes:
    ```sql
    INSERT INTO ideas (title, description, org_id, user_id) VALUES (?, ?, 'org_cricket', 'user_123');
    ```
7.  **RLS Verification:** PostgreSQL's RLS engine evaluates the insertion against the policy for the `ideas` table:
    ```sql
    USING (org_id = current_setting('app.current_org_id'))
    ```
    Since the values match, the write succeeds.
8.  **Real-Time Broadcast:** The database update is captured by the Supabase Realtime service. It evaluates the websocket subscription channel of other clients. Only clients subscribed to `ideas-changes-org_cricket` that are authorized under the database policy receive the event broadcast.

---

## Data Flow: Voting

1.  **Casting a Vote:** The client sends a `POST /api/ideas/{id}/vote` containing the `voteType` ('UP' or 'DOWN').
2.  **Context Resolution:** The backend authenticates the user and binds the `org_id` and `user_id` to the session exactly like the idea submission path.
3.  **Uniqueness Enforcement:** The system checks if the user has already voted on the target idea. The database enforces this uniqueness constraint via a composite unique index:
    ```sql
    CREATE UNIQUE INDEX UNIQUE_USER_VOTE ON votes (idea_id, user_id, org_id);
    ```
4.  **Insert or Update:** The repository attempts to insert the vote. If a record already exists, it updates the existing vote type (handling changes from upvote to downvote).
5.  **Score Recalculation:** Upon a successful vote write, the database triggers recalculation of the idea's aggregate upvote/downvote counters and updates its hot score.

---

## Ranking Algorithm

Synapse Hub ranks ideas using a gravity-based logarithmic decay formula:

$$\text{Score} = \log_{10}(\max(1, \text{Upvotes} - \text{Downvotes})) + \frac{\text{SecondsSinceEpoch}}{45000}$$

*   **Logarithmic Vote Scale:** The term $\log_{10}(\max(1, \text{NetVotes}))$ dampens the impact of large vote counts. It requires exponentially more votes (10, 100, 1000) to increase the score linearly. This prevents a single viral idea from permanently dominating the top of the workspace feed.
*   **Time Gravity Divisor:** The divisor `45000` (12.5 hours) acts as the gravity coefficient. Every 12.5 hours, the time decay term increases the baseline score of newer ideas by `1.0`. Thus, an older idea must double its net vote count every 12.5 hours to remain competitive against a brand-new idea.

---

## Security Model

### Threat Model

| Threat | Mitigation |
| :--- | :--- |
| **Cross-Tenant Data Access** | The backend resolves `org_id` exclusively from database profile lookups. All query result sets are forced through PostgreSQL RLS policies matching `app.current_org_id`. |
| **Privilege Escalation** | Admin operations (e.g. updating idea status) enforce `@PreAuthorize("hasRole('ADMIN')")` check checks. The workspace admin role is derived from the database user record, not the JWT token. |
| **Stored XSS** | React automatically escapes string values on interpolation. Markdown rendering utilizes sanitized parsers. |
| **SQL Injection** | All database transactions use Spring Data JPA parameterized queries; raw SQL string concatenations are blocked. |
| **Secret Leakage** | Clerk credentials, passwords, and database connection strings are loaded via a git-ignored `.env` file. |
| **MUTATION Abuse / Spam** | Mutating endpoints are guarded by a thread-safe token-bucket rate limiter. |
| **Realtime Channel Leaks** | Webhook subscriptions are authorized by Supabase using the same RLS database credentials. |
| **CSRF / MITM** | Stateless bearer token authentication is used (no browser cookies). HTTPS is enforced at the Nginx proxy layer. |

### Defense-in-Depth Layers

1.  **Transport Security (Nginx):** TLS/HTTPS terminated at Nginx. HSTS headers prevent downgrade attacks.
2.  **Identity Verification (Spring Security):** Token validation blocks unauthenticated sessions.
3.  **Workspace Boundary Verification (Tenant Filter):** Dynamically binds the session context, isolating user threads.
4.  **Database Level Isolation (PostgreSQL RLS):** A final, application-independent security boundary. Even if a developer writes an unsecured SQL query, the database filters the records.
5.  **Rate Limiter:** Restricts high-frequency write calls, preventing database exhaustion.

### Why Row-Level Security Specifically

Relying on application-layer filtering (adding `WHERE org_id = ?` to every query) creates a fragile system. A single developer omitting the `WHERE` clause during a feature release results in a cross-tenant data breach.

PostgreSQL Row-Level Security operates as a global safety net. Because RLS is defined on the schema tables, the boundary is enforced regardless of what application code runs. This moves security verification out of mutable Java source code and into declarative database DDL.

---

## Authorization Matrix

| Action | Guest | Member | Workspace Admin | System Admin |
| :--- | :--- | :--- | :--- | :--- |
| **Request Workspace** | Yes | Yes | Yes | Yes |
| **Approve Workspace** | No | No | No | Yes |
| **Submit Idea** | No | Yes | Yes | No |
| **Vote on Idea** | No | Yes | Yes | No |
| **Comment & Reply** | No | Yes | Yes | No |
| **Change Idea Status** | No | No | Yes | No |
| **Delete Spam Idea** | No | No | Yes | No |

---

## Failure Scenarios Considered & Tested

### Scenario A: Direct IDOR Query Attempt
An attacker in `org_A` attempts to access details of an idea belonging to `org_B` by executing a direct query via Postman: `GET /api/ideas/999` (where idea 999 belongs to `org_B`).

*   **Tested Behavior:** The request passes the filter. The backend resolves the attacker's context (`app.current_org_id = 'org_A'`). The database executes the query. Due to RLS, the database reports that the row does not exist. The application returns `404 Not Found`, giving zero indication that the ID exists.

### Scenario B: Concurrent Sandbox Profile Init Race
A brand-new user logs in. Multiple frontend requests (loading available workspaces, checking request queues) fire simultaneously before the profile registration endpoint has written the user to the database.

*   **Tested Behavior:** `TenantContextFilter` intercepts the first request and runs `userRepository.findById()`. Finding no record, it locks the context, writes the default user profile with `org_id = null`, and commits. Subsequent queries resolve cleanly from the committed profile, preventing duplicate key violations and null pointers.

<!-- Sandbox authentication walkthrough updated. -->
<!-- Sandbox authentication walkthrough updated. -->
<!-- Sandbox authentication walkthrough updated. -->