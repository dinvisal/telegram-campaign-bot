package com.example.campaignbot.service;

import com.example.campaignbot.dto.RawCampaign;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Converts raw Facebook Graph API JSON nodes into {@link RawCampaign} records.
 * Stateless — safe to share across threads.
 */
@Slf4j
@Component
public class CampaignParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Parse a single campaign JSON node into a {@link RawCampaign}.
     */
    public RawCampaign parse(JsonNode node, String pageId) {

        JsonNode insights = node.path("insights").path("data");
        JsonNode firstInsight = insights.isArray() && !insights.isEmpty()
                ? insights.get(0)
                : null;

        long messageCount = 0L;
        BigDecimal costPerMessage = BigDecimal.ZERO;
        if (firstInsight != null) {
            messageCount = extractMessageCount(firstInsight);
            costPerMessage = extractCostPerMessage(firstInsight);
        }

        return RawCampaign.builder()
                .campaignId(node.path("id").asText())
                .campaignName(node.path("name").asText("Untitled"))
                .status(node.path("status").asText("UNKNOWN"))
                .effectiveStatus(node.path("effective_status").asText("UNKNOWN"))
                .objective(node.hasNonNull("objective")
                        ? node.get("objective").asText()
                        : null)
                .dailyBudget(node.hasNonNull("daily_budget")
                        ? parseCents(node.get("daily_budget").asText())
                        : null)
                .lifetimeBudget(node.hasNonNull("lifetime_budget")
                        ? parseCents(node.get("lifetime_budget").asText())
                        : null)
                .startDate(parseDate(node.get("start_time")))
                .endDate(parseDate(node.get("stop_time")))
                .spend(firstInsight != null
                        ? parseAmount(firstInsight.path("spend").asText("0"))
                        : BigDecimal.ZERO)
                .impressions(firstInsight != null
                        ? firstInsight.path("impressions").asLong(0)
                        : 0L)
                .clicks(firstInsight != null
                        ? firstInsight.path("clicks").asLong(0)
                        : 0L)
                .messageCount(messageCount)
                .costPerMessage(costPerMessage)
                .pageId(pageId)
                .build();
    }

    private long extractMessageCount(JsonNode insight) {
        JsonNode actions = insight.path("actions");
        if (actions.isArray()) {
            for (JsonNode action : actions) {
                if ("onsite_conversion.messaging_conversation_started_7d"
                        .equals(action.path("action_type").asText())) {
                    return action.path("value").asLong(0);
                }
            }
        }
        return 0L;
    }

    private BigDecimal extractCostPerMessage(JsonNode insight) {
        JsonNode costs = insight.path("cost_per_action_type");
        if (costs.isArray()) {
            for (JsonNode cost : costs) {
                if ("onsite_conversion.messaging_conversation_started_7d"
                        .equals(cost.path("action_type").asText())) {
                    return parseAmount(cost.path("value").asText("0"));
                }
            }
        }
        return BigDecimal.ZERO;
    }

    BigDecimal parseCents(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("Unable to parse budget value: {}", value);
            return BigDecimal.ZERO;
        }
    }

    BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("Unable to parse spend value: {}", value);
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(JsonNode dateNode) {
        if (dateNode == null || dateNode.isNull()) {
            return null;
        }
        try {
            String text = dateNode.asText();
            if (text.length() >= 10) {
                return LocalDate.parse(text.substring(0, 10), DATE_FMT);
            }
        } catch (Exception e) {
            log.debug("Unable to parse Facebook date: {}", dateNode);
        }
        return null;
    }
}
