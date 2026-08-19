# Ads Manager Reporting Bot

A Telegram Bot built with **Spring Boot** that connects to the **Facebook Marketing API** to collect advertising campaign metrics and generate reporting directly in Telegram.

The bot synchronizes campaign information into PostgreSQL and provides commands to monitor campaign performance such as today's spend, message count, cost per message, budgets, and campaign status.

---

# Features

- Synchronize Facebook Ads campaigns
- Daily campaign reporting
- Today's spend reporting
- Message count reporting
- Cost per message reporting
- Campaign budget reporting
- Multiple Facebook Pages support
- PostgreSQL persistence
- Telegram Bot interface
- Docker support

---

# Technology Stack

- Java
- JRE 21+
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Telegram Bot API
- Facebook Marketing API
- Maven
- Docker

---

# Requirements

- Java JRE 21 or later
- Maven 3.9+
- PostgreSQL
- Facebook Marketing API Access Token
- Telegram Bot Token

---

# Configuration

Configure the following environment variables before running:

| Variable | Description |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `TELEGRAM_BOT_TOKEN` | Telegram Bot Token |
| `TELEGRAM_REPORT_CHAT_ID` | Telegram chat ID receiving the scheduled 08:00 / 21:00 campaign report (default `399337142`) |

Example:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/campaign_bot
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
TELEGRAM_BOT_TOKEN=xxxxxxxxxxxxxxxx
TELEGRAM_REPORT_CHAT_ID=682472724
```

---

# Database

The project uses PostgreSQL.

Schema initialization is located in:

```
src/main/resources/schema.sql
```

---

# Run Locally

Clone the repository

```bash
git clone <repository-url>
cd telegram-campaign-bot
```

Run the application

```bash
mvn clean spring-boot:run
```

The application will start on:

```
http://localhost:8080
```

---

# Build

```bash
mvn clean package
```

Generated artifact:

```
target/telegram-campaign-bot.jar
```

Run the JAR

```bash
java -jar target/telegram-campaign-bot.jar
```

---

# Run with Docker

## Build Docker Image

```bash
docker build -t ads-manager-reporting-bot .
```

## Run Container

```bash
docker run -d \
  --name ads-manager-reporting-bot \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/campaign_bot \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  -e TELEGRAM_BOT_TOKEN=YOUR_TELEGRAM_BOT_TOKEN \
  ads-manager-reporting-bot
```

---

# Docker Compose

Example `docker-compose.yml`

```yaml
version: "3.9"

services:

  postgres:
    image: postgres:16
    container_name: campaign-db

    environment:
      POSTGRES_DB: campaign_bot
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres

    ports:
      - "127.0.0.1:5432:5432"

  ads-manager-reporting-bot:
    build: .

    depends_on:
      - postgres

    ports:
      - "8080:8080"

    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/campaign_bot
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      TELEGRAM_BOT_TOKEN: YOUR_TELEGRAM_BOT_TOKEN
```

Run

```bash
docker compose up --build
```

---

# Project Structure

```
src
├── main
│   ├── java
│   │   └── com.example.campaignbot
│   │       ├── bot
│   │       ├── controller
│   │       ├── entity
│   │       ├── repository
│   │       ├── service
│   │       └── config
│   └── resources
│       ├── application.yml
│       └── schema.sql
```

---

# Available Reports

- Today's Active Campaigns
- Daily Spend
- Message Count
- Cost Per Message
- Campaign Budget
- Campaign Status

---

# License

This project is intended for internal use.