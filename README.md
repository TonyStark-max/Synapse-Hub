# Synapse Hub

A multi-tenant feedback and idea platform for internal teams, featuring database-level tenant isolation enforced via PostgreSQL Row-Level Security (RLS) and a zero-config developer sandbox.

## Problem Statement

Teams often have product feedback, feature requests, and internal ideas scattered across disjointed channels like Slack, email, and meetings, with no structured way to consolidate, rank, and track them. Synapse Hub resolves this by providing a unified workspace where employees can submit suggestions, participate in structured discussions, vote on priorities, and track ideas through a transparent decision-making lifecycle.

## What It Does

*   **Logarithmic Idea Ranking:** Ranks submissions using a gravity-based time-decay algorithm that balances vote counts against submission recency, preventing old ideas from permanently dominating the top feed.
*   **Database-Level Tenant Isolation:** Enforces absolute data boundaries between workspaces directly at the PostgreSQL layer via Row-Level Security (RLS), preventing cross-tenant leakage.
*   **Real-time Collaboration:** Leverages real-time channels to broadcast new ideas, votes, and comments to active workspace members instantly.
*   **Zero-Dependency Local Sandbox:** Integrates a mock authentication provider that enables immediate, single-command full-stack testing (with admin and member roles) without requiring Clerk registration.
*   **Structured Lifecycle Tracking:** Allows workspace administrators to review, approve, reject, and update the status (e.g., Planned, In Progress, Completed) of submitted ideas.

## What It Deliberately Doesn't Do

*   **Cross-Tenant Search / Sharing:** Workspaces are strictly siloed; members cannot search or view ideas from other organizations under any circumstance.
*   **Anonymous Submissions:** To maintain accountability within internal teams, all ideas, votes, and comments must be tied to a verified user profile.
*   **Automated Duplicate Detection:** Scoping is kept clean; managing duplicate suggestions is handled manually by workspace administrators.
*   **Native Mobile Application:** The platform is built and optimized exclusively as a responsive web SPA.

## Architecture at a Glance

```
[Browser Client] ---> [Nginx Proxy] ---> [Spring Boot API (Spring Security)]
                                                   │ (Extracts User / Tenant Context)
                                                   ▼
[Supabase Realtime] <--- (RLS Filtered) <--- [PostgreSQL (Row-Level Security)]
```

*   **Authentication:** The browser client obtains a JWT token (from Clerk or the local sandbox) and attaches it to request headers.
*   **Security Context:** The Spring Boot backend decodes the token, loads the user's active database workspace profile, and binds the tenant scope to the PostgreSQL session transaction.
*   **Isolation Enforcement:** PostgreSQL evaluates Row-Level Security policies on the target tables (`ideas`, `comments`, `votes`), filtering the result sets regardless of application query logic.
*   *Detailed design rationale, threat models, and security architectures are documented in [ARCHITECTURE.md](./ARCHITECTURE.md).*

## Tech Stack

*   **Backend:** Java 21, Spring Boot 3, Spring Security (OAuth2 JWT verification).
*   **Database:** PostgreSQL 16 (Row-Level Security enabled).
*   **Real-time:** Supabase Realtime (real-time PostgreSQL change broadcasts).
*   **Frontend:** React 18, TypeScript, Vite, Vanilla CSS.
*   **Authentication:** Clerk SSO (Production) / Local Mock JWT Sandbox (Development).
*   **Containerization:** Docker, Docker Compose, Nginx.

## Getting Started

### Prerequisites
*   Docker & Docker Compose (v2.0+)
*   Node.js (v20+) & NPM (for manual local run)
*   JDK 21 & Maven 3.9+ (for manual backend run)

### Environment Setup (Optional)
By default, the docker-compose template will boot straight into Sandbox Mode automatically with zero configuration. 

If you want to configure real Clerk SSO, copy the template and edit your credentials:
1. Copy the example template:
   ```bash
   cp .env.example .env
   ```
2. Open `.env` and fill in your Clerk secret keys.

### Running Locally
To launch the database, backend, and frontend containers in a single command:
```bash
docker compose up --build -d
```
*   The web application will be accessible at **[http://localhost](http://localhost)** (Port `80`).
*   Sign in with `lostg826@gmail.com` to act as the System Administrator and approve workspace requests.

### Running Tests
To run the Spring Boot integration and security test suite:
```bash
cd backend
mvn clean test
```

---

## Core Concepts

### Organizations & Multi-Tenancy
Every company, team, or department operates inside its own isolated organization workspace. Workspaces are requested by users and approved by the system administrator. Once joined, a user's access is restricted to that organization's data. Multi-tenancy is secured at the database layer using PostgreSQL Row-Level Security policies, ensuring that database connections can never read cross-tenant rows.

### Ranking Algorithm
Ideas are ranked using a gravity-based logarithmic time decay formula:
$$\text{Score} = \log_{10}(\max(1, \text{Upvotes} - \text{Downvotes})) + \frac{\text{SecondsSinceEpoch}}{45000}$$
*   The logarithmic term ensures that the first 10 votes have the same impact on the ranking as the next 100, preventing a single highly popular idea from permanently blocking the feed.
*   The divisor `45000` acts as the gravity factor (approx. 12.5 hours half-life), causing older ideas to lose score value over time so newer topics can surface.

### Roles & Permissions

| Role | Access Scope | Allowed Actions |
| :--- | :--- | :--- |
| **System Admin** | Global Platform | Approve/Reject workspace creation requests. |
| **Workspace Admin** | Tenant-Scoped | Update idea lifecycle statuses, delete spam ideas/comments. |
| **Member** | Tenant-Scoped | Submit ideas, cast votes, post comments & replies. |
| **Guest** | Open Access | View active workspaces, request workspace creation. |

---

## Security
Tenant isolation is enforced independently at both the application layer and via PostgreSQL Row-Level Security. See [ARCHITECTURE.md](./ARCHITECTURE.md) for the full threat model and [SECURITY-CHECKLIST.md](./idea-platform-SECURITY-CHECKLIST.md) for the pre-launch verification process.

## Deployment
The full stack is deployed containerized on a Virtual Private Server (VPS) using Docker Compose, providing a flat-rate hosting model (PostgreSQL database + Spring Boot backend + Nginx frontend) for a fraction of the cost of serverless container architectures.

## License
Distributed under the MIT License. See `LICENSE` for more information.

<!-- Getting started section simplified. -->
<!-- Getting started section simplified. -->
<!-- Getting started section simplified. -->