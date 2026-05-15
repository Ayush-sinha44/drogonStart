# 🐉 Drogon Project Scaffolder — Backend

A Spring Boot backend that works like [Spring Initializr](https://start.spring.io/) but for **C++ [Drogon](https://github.com/drogonframework/drogon) web framework projects**. Send a POST request with your project configuration, and get back a ready-to-use zipped C++ project — generated inside an ephemeral Docker container running `drogon_ctl`.



---

## How It Works

```
Client Request
     │
     ▼
Spring Boot API  ──►  Spins up ephemeral Docker container
                              │
                              ▼
                       drogon_ctl generates C++ project
                              │
                              ▼
                       Project zipped with Apache Commons Compress
                              │
                              ▼
                       ZIP served for download  ──►  Container destroyed
```

1. Client POSTs project config (name, controllers, views, etc.)
2. Backend spawns a short-lived Docker container with `drogon_ctl` installed
3. Container generates the C++ project structure
4. Backend zips the output and streams it back as a download
5. Container is cleaned up automatically

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| Database | PostgreSQL (via Docker) |
| ORM | Hibernate / Spring Data JPA |
| Docker integration | Docker Java SDK |
| ZIP creation | Apache Commons Compress |
| Async processing | Spring `@Async` |
| Build tool | Maven |

---

## Prerequisites

Make sure you have the following installed:

- **Java 21+**
- **Maven 3.8+**
- **Docker** (the backend spawns containers at runtime — Docker must be running)
- **Docker Compose**

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/Ayush-sinha44/drogonStart.git
cd drogon-scaffolder-backend
```

### 2. Create your `.env` file

Create a `.env` file in the project root. 

```bash
cp .env.example .env
```

Then edit `.env` and fill in your values:

```env
POSTGRES_PASSWORD="your_password_here"
POSTGRES_USER=scaffolder
POSTGRES_DB=scaffolder_db
```

> ⚠️ If your password contains special characters like `@`, make sure to **wrap it in quotes** in the `.env` file, otherwise shell parsing will break.

### 3. Start the database

```bash
docker-compose up -d
```

This starts a PostgreSQL container using the credentials from your `.env` file.

### 4. Run the application

Use the provided `run.sh` script, which loads `.env` into the shell before starting Spring Boot:

```bash
chmod +x run.sh
./run.sh
```

> **Why `run.sh` and not `mvn spring-boot:run` directly?**
> Docker Compose automatically reads `.env` for the container, but Maven does not. The `run.sh` script sources `.env` into the shell environment first, so Spring Boot can resolve `${POSTGRES_PASSWORD}` in `application.yml`. This is standard practice for local dev with secret separation.

The API will be available at `http://localhost:8080`.

---

## Project Structure

```
drogon-scaffolder-backend/
├── src/
│   └── main/
│       ├── java/com/ayush/drogonStart/
│       │   ├── controller/       # REST endpoints
│       │   ├── service/          # Business logic, Docker orchestration
│       │   ├── model/            # JPA entities
│       │   ├── repository/       # Spring Data repositories
│       │   └── DrogonStartApplication.java
│       └── resources/
│           └── application.yml
├── docker-compose.yml            # PostgreSQL container
├── .env                          # Local secrets (gitignored)
├── .env.example                  # Template for .env
├── run.sh                        # Dev startup script
└── pom.xml
```

---

## Environment Variables

| Variable | Description | Example |
|---|---|---|
| `POSTGRES_PASSWORD` | Password for the `scaffolder` DB user | `"mypassword@123"` |
| `POSTGRES_USER` | PostgreSQL username | `scaffolder` |
| `POSTGRES_DB` | Database name | `scaffolder_db` |

These are referenced in `application.yml` as `${POSTGRES_PASSWORD}` etc., and in `docker-compose.yml` for container initialization.

---

## API Overview

### `POST /api/scaffold`

Generates and returns a zipped Drogon C++ project.

**Request body:**
```json
{
  "projectName": "my-api",
  "controllers": ["UserController", "AuthController"],
  "enableViews": false
}
```

**Response:** `application/zip` — a downloadable `.zip` file containing the generated project.

---

## Production Considerations

The following production-readiness features are implemented 

- **IP-based rate limiting** — prevents abuse of the Docker spawning endpoint
- **Strict input validation** — project name, controller names sanitized before passing to `drogon_ctl`
- **Resource limits** — containers run with 512MB RAM cap, 1 CPU, 120s timeout, and `--network none`
- **Async job processing** — scaffold jobs are handled asynchronously with `@Async`
- **Health checks** — Spring Actuator endpoints for container orchestration readiness
- **Cleanup jobs** — scheduled tasks to remove orphaned containers and temp files



## Troubleshooting

**`password authentication failed for user "scaffolder"`**

Your shell doesn't have the env vars loaded. Use `./run.sh` instead of `mvn spring-boot:run` directly, or run:
```bash
export $(cat .env | xargs) && mvn spring-boot:run
```

**`Permission denied` on `run.sh`**
```bash
chmod +x run.sh
```

**Docker container fails to start**

Make sure Docker daemon is running:
```bash
sudo systemctl start docker
# or
docker info
```

**Volume has stale credentials after password change**
```bash
docker-compose down -v   # removes the volume, DB will reinitialize
docker-compose up -d
```

---

## Related

- [Drogon Framework](https://github.com/drogonframework/drogon) — the C++ web framework this tool scaffolds projects for
- [Spring Initializr](https://start.spring.io/) — the inspiration for this project's UX
