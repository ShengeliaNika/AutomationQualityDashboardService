# Docker Setup Guide

This guide explains how to run the Automation Quality Dashboard Service using Docker Compose.

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+

## Quick Start

### 1. Build and Start Services

```bash
# Start all services (PostgreSQL + Application)
docker-compose up -d

# View logs
docker-compose logs -f

# View application logs only
docker-compose logs -f dashboard
```

The application will be available at `http://localhost:8080`

### 2. Stop Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes all data)
docker-compose down -v
```

## Configuration

### Using Environment Variables

1. Copy the example environment file:
```bash
cp .env.example .env
```

2. Edit `.env` file with your configuration:
```env
POSTGRES_DB=dashboarddb
POSTGRES_USER=dashboard
POSTGRES_PASSWORD=your-secure-password
POSTGRES_PORT=5432
APP_PORT=8080
```

3. Start services:
```bash
docker-compose up -d
```

### Default Configuration

If no `.env` file is present, the following defaults are used:
- Database Name: `dashboarddb`
- Database User: `dashboard`
- Database Password: `dashboard`
- PostgreSQL Port: `5432`
- Application Port: `8080`

## Services

### PostgreSQL Database
- **Container Name**: `dashboard-postgres`
- **Image**: `postgres:15-alpine`
- **Port**: `5432` (configurable via `POSTGRES_PORT`)
- **Data Volume**: `postgres-data` (persists data)
- **Health Check**: Checks if PostgreSQL is ready every 10 seconds

### Dashboard Application
- **Container Name**: `dashboard-app`
- **Build**: From local Dockerfile
- **Port**: `8080` (configurable via `APP_PORT`)
- **Depends On**: PostgreSQL (waits for health check)
- **Restart Policy**: `unless-stopped`

## Useful Commands

### Build Only
```bash
# Rebuild images without starting
docker-compose build

# Rebuild with no cache
docker-compose build --no-cache
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f dashboard
docker-compose logs -f postgres

# Last 100 lines
docker-compose logs --tail=100
```

### Database Access
```bash
# Connect to PostgreSQL
docker exec -it dashboard-postgres psql -U dashboard -d dashboarddb

# Run SQL commands
docker exec -it dashboard-postgres psql -U dashboard -d dashboarddb -c "SELECT * FROM test_runs LIMIT 5;"
```

### Service Management
```bash
# Start services
docker-compose start

# Stop services (without removing)
docker-compose stop

# Restart services
docker-compose restart

# Restart specific service
docker-compose restart dashboard
```

### Cleanup
```bash
# Remove stopped containers
docker-compose rm

# Remove everything including volumes
docker-compose down -v

# Remove images
docker-compose down --rmi all
```

## Troubleshooting

### Port Already in Use

If port 8080 or 5432 is already in use:

1. **Option 1**: Change ports in `.env` file:
```env
POSTGRES_PORT=5433
APP_PORT=8081
```

2. **Option 2**: Stop conflicting services:
```bash
# Find process using port 8080
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Kill the process or stop the service
```

### Database Connection Errors

If the application fails to connect to the database:

1. Check PostgreSQL is running:
```bash
docker-compose ps
```

2. Check PostgreSQL logs:
```bash
docker-compose logs postgres
```

3. Verify health check:
```bash
docker inspect dashboard-postgres | grep Health
```

4. Manually test connection:
```bash
docker exec -it dashboard-postgres psql -U dashboard -d dashboarddb
```

### Application Not Starting

1. Check application logs:
```bash
docker-compose logs dashboard
```

2. Verify the image built successfully:
```bash
docker images | grep dashboard
```

3. Rebuild the image:
```bash
docker-compose build --no-cache dashboard
docker-compose up -d
```

### Data Persistence

Data is stored in a Docker volume named `postgres-data`. To backup:

```bash
# Backup database
docker exec dashboard-postgres pg_dump -U dashboard dashboarddb > backup.sql

# Restore database
cat backup.sql | docker exec -i dashboard-postgres psql -U dashboard dashboarddb
```

## Development Mode

For development with hot reload (requires Maven on host):

```bash
# Run only PostgreSQL
docker-compose up -d postgres

# Run application locally
mvn spring-boot:run
```

## Production Deployment

For production, consider:

1. **Use secrets management**:
   - Don't use `.env` file in production
   - Use Docker Secrets or environment-specific configuration

2. **Use specific image tags**:
   - Tag your images with version numbers
   - Don't use `latest` in production

3. **Add resource limits**:
```yaml
services:
  dashboard:
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M
```

4. **Configure logging**:
```yaml
services:
  dashboard:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

5. **Enable TLS**:
   - Use a reverse proxy (nginx/traefik)
   - Configure SSL certificates

## Network Architecture

```
┌─────────────────────────────────────┐
│         Host Machine                │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   dashboard-network          │  │
│  │   (bridge)                   │  │
│  │                              │  │
│  │  ┌────────────────────────┐ │  │
│  │  │  dashboard-postgres    │ │  │
│  │  │  Port: 5432           │ │  │
│  │  │  Volume: postgres-data│ │  │
│  │  └────────────────────────┘ │  │
│  │            ↑                 │  │
│  │            │                 │  │
│  │  ┌────────────────────────┐ │  │
│  │  │  dashboard-app         │ │  │
│  │  │  Port: 8080           │ │  │
│  │  └────────────────────────┘ │  │
│  └──────────────────────────────┘  │
│           ↑                         │
│           │                         │
└───────────┼─────────────────────────┘
            │
      Port Mappings:
      5432:5432 → PostgreSQL
      8080:8080 → API
```

## Testing the Setup

After starting services, test the API:

```bash
# Health check (if implemented)
curl http://localhost:8080/actuator/health

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

# Get dashboard
curl http://localhost:8080/dashboard
```

## Support

For issues or questions:
1. Check logs: `docker-compose logs`
2. Verify configuration: `docker-compose config`
3. Open an issue on GitHub
