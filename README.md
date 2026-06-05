# Automation Quality Dashboard Service

A Java-based reporting and analytics service that collects automation test run results, analyzes flaky tests, and generates comprehensive dashboards and HTML reports.

## Features

- Test Run Management - Store and retrieve test execution results
- Dashboard Analytics - View trends for pass rates, failures, and durations
- Flaky Test Detection - Automatically identify unreliable tests
- Performance Insights - Track slowest tests across runs
- HTML Reports - Generate professional, shareable test reports
- Webhook Notifications - Optional callback notifications on run completion

## Tech Stack

- Java 17
- Spring Boot 3.1.5
- PostgreSQL - Production database
- H2 - In-memory database for tests
- JUnit 5 - Testing framework
- Mockito - Mocking framework
- WireMock - HTTP mocking for integration tests
- Docker - Containerization

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+ (for local development)
- Docker (optional, for containerized deployment)

## Quick Start

### 1. Database Setup

Create a PostgreSQL database:

CREATE DATABASE dashboarddb;
CREATE USER dashboard WITH PASSWORD 'dashboard';
GRANT ALL PRIVILEGES ON DATABASE dashboarddb TO dashboard;

### 2. Run Locally

Clone and build:
mvn clean install
mvn spring-boot:run

The service will start on http://localhost:8080

### 3. Run with Docker

docker build -t automation-dashboard:latest .
docker run -d -p 8080:8080 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/dashboarddb automation-dashboard:latest

## API Endpoints

POST /runs - Save test run
GET /runs/{runId}/summary - Get run summary
GET /runs/{runId}/report - Get HTML report
GET /dashboard?branch=main&environment=test&lastN=10 - Get dashboard data
GET /tests/flaky - Get flaky tests
GET /tests/slowest?limit=10 - Get slowest tests

## Configuration

Configure via application.yml or environment variables:
- DB_URL (default: jdbc:postgresql://localhost:5432/dashboarddb)
- DB_USERNAME (default: dashboard)
- DB_PASSWORD (default: dashboard)
- SERVER_PORT (default: 8080)

## Flaky Test Detection Logic

A test is considered flaky if in the last 5 executions it has at least 1 PASSED and 1 FAILED status.

## Testing

Run all tests:
mvn test

## Architecture

Controllers -> Services -> Repositories -> PostgreSQL

Key Components:
- Controllers: Handle HTTP requests/responses
- Services: Business logic for test analytics
- Repositories: JPA repositories for database access
- DTOs: Request/Response data transfer objects
- Entities: JPA entities (TestRun, TestResult)

## Performance Optimizations

- Database-level sorting
- Pagination with Pageable
- Read-only transactions
- JOIN FETCH to avoid N+1 queries

## License

MIT License
