# Code Improvement Plan — telegram-campaign-bot

## 1. Critical Bugs

### 1.1 `verifyAdAccountAccess` called inside pagination loop
**File:** [FacebookAdsService.java:154](src/main/java/com/example/campaignbot/service/FacebookAdsService.java:154)

The account verification call is made on every iteration of the `do-while` pagination loop. If a page has 300 campaigns (3 pages of 100), it makes 3 redundant verification calls. Move it before the loop.

### 1.2 Orphaned campaigns after deactivation
**File:** [CampaignService.java:59-107](src/main/java/com/example/campaignbot/service/CampaignService.java:59)

`syncPage()` only upserts campaigns that Facebook returns as ACTIVE. Campaigns that were previously synced but later paused/deleted on Facebook are never updated — they remain stale in the database with `status = 'ACTIVE'`. Add a step to mark non-returned campaigns as inactive.

### 1.3 Duplicate `escapeHtml` + redundant escape in service layer
**Files:** [CampaignBot.java:273](src/main/java/com/example/campaignbot/bot/CampaignBot.java:273), [FacebookAdsService.java:578](src/main/java/com/example/campaignbot/service/FacebookAdsService.java:578)

`escapeHtml` is duplicated. Worse, `FacebookAdsService` escapes HTML at parse time (line 325-326), so the database stores escaped HTML strings. If the data is ever consumed by a non-Telegram client or exported, it contains `&amp;` and `&lt;` in the raw values. HTML escaping should happen only at the presentation layer (`CampaignBot`), not at the data layer.

---

## 2. Code Quality

### 2.1 `handleToday` is a 100-line monolith
**File:** [CampaignBot.java:120-218](src/main/java/com/example/campaignbot/bot/CampaignBot.java:120)

The method mixes data fetching, formatting, HTML escaping, and message sending. Extract:
- A `CampaignFormatter` or `ReportFormatter` class that builds the report string.
- Keep the bot class focused on dispatch and message routing.

### 2.2 `RawCampaign` should be a Java 21 record
**File:** [FacebookAdsService.java:493-523](src/main/java/com/example/campaignbot/service/FacebookAdsService.java:493)

The project targets Java 21 but uses Lombok `@Value` + `@Builder` for a simple DTO. A record would be cleaner and more idiomatic:

```java
public record RawCampaign(
    String campaignId,
    String campaignName,
    // ...
    Long messageCount,
    BigDecimal costPerMessage
) {}
```

### 2.3 Redundant annotations on `FacebookPage`
**File:** [FacebookPage.java:40-56](src/main/java/com/example/campaignbot/entity/FacebookPage.java:40)

Both `@CreationTimestamp`/`@UpdateTimestamp` and `@PrePersist`/`@PreUpdate` are used. The `@Pre` lifecycle callbacks override the Hibernate annotations, making them dead code. Pick one approach. The Hibernate annotations are simpler.

### 2.4 Redundant setter in `syncPage`
**File:** [CampaignService.java:90](src/main/java/com/example/campaignbot/service/CampaignService.java:90)

`campaign.setCampaignId(raw.getCampaignId())` is already set in the builder on line 84-85. Remove it.

### 2.5 No batch save
**File:** [CampaignService.java:106](src/main/java/com/example/campaignbot/service/CampaignService.java:106)

`campaignRepository.save(campaign)` is called in a loop. Use `saveAll()` with a list for a single transaction batch.

---

## 3. Production Readiness

### 3.1 `ddl-auto: update` in production
**File:** [application.yml:22](src/main/resources/application.yml:22)

Hibernate auto-DDL can corrupt data on schema changes. Use `validate` (or `none`) and add Flyway or Liquibase for migrations.

### 3.2 No exception handling in TelegramMessageService
**File:** [TelegramMessageService.java:29-52](src/main/java/com/example/campaignbot/service/TelegramMessageService.java:29)

If Telegram's API is down or rate-limited, the exception propagates up to the bot and crashes the consumer callback. Add a try-catch with logging, and consider retry with backoff.

### 3.3 Logging entire Facebook response at DEBUG
**File:** [FacebookAdsService.java:185](src/main/java/com/example/campaignbot/service/FacebookAdsService.java:185)

`log.debug(response)` logs the full response body. If the response ever includes an access token in a redirect or error, it leaks credentials. Remove this or sanitize it.

### 3.4 No rate limiting or circuit breaker
**File:** [FacebookAdsService.java:44-274](src/main/java/com/example/campaignbot/service/FacebookAdsService.java:44)

Facebook's Marketing API has strict rate limits. A sync of many pages could hit them with no backoff. Add Resilience4j or a simple delay between page syncs.

### 3.5 Hardcoded Graph API version
**File:** [FacebookAdsService.java:25](src/main/java/com/example/campaignbot/service/FacebookAdsService.java:25)

`v19.0` is hardcoded and will eventually be deprecated by Meta. Move to a configuration property.

### 3.6 No health check endpoint
The Docker container exposes port 8080 but there's no `/actuator/health` or similar endpoint. Spring Boot Actuator should be added.

### 3.7 No tests
The project has zero test classes despite `spring-boot-starter-test` being in the POM. Critical areas to test:
- `FacebookAdsService.parseCampaign()` — JSON parsing with edge cases
- `CampaignService.getTodaysActiveCampaigns()` — grouping logic
- `CampaignBot.consume()` — command routing

---

## 4. Architecture / Separation of Concerns

### 4.1 Add a DTO/Formatter layer
Raw data flows directly from JPA entities into the bot's message builder. Adding a `CampaignReportFormatter` would:
- Make formatting testable without Telegram dependencies.
- Allow different output formats (HTML, plain text, CSV export) later.

### 4.2 Extract Facebook API client
`FacebookAdsService` does everything: builds URIs, handles pagination, parses JSON, and converts currencies. Split into:
- `FacebookAdsClient` — raw API calls, pagination, error handling.
- `CampaignParser` — JSON → `RawCampaign` conversion.
- Keep `FacebookAdsService` as the orchestrator.

---

## 5. Quick Wins (Low Effort, High Impact)

| Priority | Change | File |
|----------|--------|------|
| **P0** | Move `escapeHtml` out of data layer (store raw data) | FacebookAdsService, CampaignBot |
| **P0** | Mark missing campaigns as inactive during sync | CampaignService |
| **P0** | Move `verifyAdAccountAccess` outside pagination loop | FacebookAdsService |
| **P1** | Remove duplicate `escapeHtml` — use a shared util | Both files |
| **P1** | Use `saveAll()` instead of looped `save()` | CampaignService |
| **P1** | Remove redundant `@PrePersist/@PreUpdate` on FacebookPage | FacebookPage |
| **P1** | Add try-catch to TelegramMessageService | TelegramMessageService |
| **P1** | Change `ddl-auto` to `validate` | application.yml |
| **P2** | Convert `RawCampaign` to a record | FacebookAdsService |
| **P2** | Extract report formatting from `handleToday` | CampaignBot |
| **P2** | Add Spring Boot Actuator for health checks | pom.xml, application.yml |
| **P2** | Add Flyway for schema migrations | pom.xml |
| **P3** | Add Resilience4j circuit breaker | pom.xml |
| **P3** | Write unit tests for core logic | New test classes |
| **P3** | Externalize Graph API version to config | application.yml |

---

## 6. Suggested Next Steps

1. Fix the three P0 bugs first — they affect correctness.
2. Tackle P1 reliability items (error handling, batch save, migration safety).
3. Add at least a few tests for the Facebook JSON parser before refactoring it.
4. Then do the architectural split (P2-P3) incrementally.
