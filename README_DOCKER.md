# Quick Start with Docker Compose

The fastest way to run the Automation Quality Dashboard Service is using Docker Compose.

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+

## Start the Application

### Option 1: Using the startup script (Recommended)

```bash
./start.sh
```

This will:
- Check Docker is running
- Create `.env` file if needed
- Build and start all services
- Show service status

### Option 2: Manual docker-compose

```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f
```

## Access the Application

Once started, the application is available at:
- **API**: http://localhost:8080
- **Database**: localhost:5432

## Stop the Application

```bash
# Using the stop script
./stop.sh

# Or manually
docker-compose down
```

## Test the API

```bash
# Create a test run
curl -X POST http://localhost:8080/runs \
  -H "Content-Type: application/json" \
  -d '{
    "runId": "test-001",
    "branch": "main",
    "environment": "test",
    "commitHash": "abc123",
    "startedAt": "2026-06-05T10:00:00",
    "tests": [
      {
        "testId": "test-1",
        "testName": "Sample Test",
        "suite": "Smoke",
        "status": "PASSED",
        "durationMs": 1000
      }
    ]
  }'

# Get run summary
curl http://localhost:8080/runs/test-001/summary

# Get dashboard
curl http://localhost:8080/dashboard

# Get flaky tests
curl http://localhost:8080/tests/flaky

# Get slowest tests
curl http://localhost:8080/tests/slowest?limit=5
```

## View Logs

```bash
# All services
docker-compose logs -f

# Application only
docker-compose logs -f dashboard

# Database only
docker-compose logs -f postgres
```

## Configuration

Edit `.env` file to customize:
```env
POSTGRES_DB=dashboarddb
POSTGRES_USER=dashboard
POSTGRES_PASSWORD=your-password
POSTGRES_PORT=5432
APP_PORT=8080
```

## Troubleshooting

**Port already in use?**
- Change `APP_PORT` in `.env` file

**Database connection issues?**
- Check logs: `docker-compose logs postgres`
- Verify health: `docker-compose ps`

**Need fresh start?**
```bash
docker-compose down -v  # Remove all data
docker-compose up -d --build  # Rebuild and start
```

## Full Documentation

See [DOCKER_SETUP.md](DOCKER_SETUP.md) for comprehensive documentation.
