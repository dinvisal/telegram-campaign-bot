package com.example.campaignbot.service;

import com.example.campaignbot.entity.FacebookPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FacebookAdsService {

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v19.0";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FacebookAdsService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {

        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch campaigns from the Facebook Ad Account associated
     * with the supplied Facebook Page.
     */
    public List<RawCampaign> fetchCampaigns(FacebookPage page) {

        if (page == null) {
            log.warn("Facebook page is null");
            return List.of();
        }

        if (page.getAdAccountId() == null
                || page.getAdAccountId().isBlank()) {

            log.warn(
                    "No ad account configured for page {}",
                    page.getPageId());

            return List.of();
        }

        if (page.getAccessToken() == null
                || page.getAccessToken().isBlank()) {

            log.warn(
                    "No access token configured for page {}",
                    page.getPageId());

            return List.of();
        }

        String actId = page.getAdAccountId().startsWith("act_")
                ? page.getAdAccountId()
                : "act_" + page.getAdAccountId();

        List<RawCampaign> campaigns = new ArrayList<>();

        String after = null;

        LocalDate today = LocalDate.now();
        String todayString = today.format(DATE_FMT);

        try {

            do {

                /*
                 * Do NOT manually URL encode this string.
                 *
                 * UriComponentsBuilder will encode the braces:
                 *
                 * insights{spend,impressions,clicks}
                 *
                 * into:
                 *
                 * insights%7Bspend,impressions,clicks%7D
                 */
                String fields = "id,name,status,objective,"
                        + "daily_budget,lifetime_budget,"
                        + "start_time,stop_time,"
                        + "insights.time_range("
                        + "{\"since\":\"" + todayString + "\","
                        + "\"until\":\"" + todayString + "\"}"
                        + "){spend,impressions,clicks,"
                        + "actions,cost_per_action_type}";

                UriComponentsBuilder builder = UriComponentsBuilder
                        .fromUriString(
                                GRAPH_API_BASE
                                        + "/"
                                        + actId
                                        + "/campaigns")
                        .queryParam("fields", fields)
                        .queryParam(
                                "filtering",
                                "[{\"field\":\"effective_status\","
                                        + "\"operator\":\"IN\","
                                        + "\"value\":[\"ACTIVE\"]}]")
                        .queryParam("limit", 100)
                        .queryParam(
                                "access_token",
                                page.getAccessToken());

                if (after != null && !after.isBlank()) {
                    builder.queryParam("after", after);
                }

                /*
                 * IMPORTANT FIX:
                 *
                 * Build an actual URI instead of converting it to a String.
                 *
                 * Using:
                 *
                 * .toUriString()
                 *
                 * and then passing that String to RestTemplate can result
                 * in double encoding:
                 *
                 * %7B -> %257B
                 *
                 * Passing URI directly prevents that.
                 */
                URI uri = builder
                        .build()
                        .toUri();
                log.info(
                        "Facebook campaign request URI: {}",
                        uri.toASCIIString());

                // String response = restTemplate.getForObject(
                // uri,
                // String.class
                // );
                if (!verifyAdAccountAccess(
                        actId,
                        page.getAccessToken(),
                        page.getPageId())) {

                    log.error(
                            "Facebook ad account {} is not accessible for page {}",
                            actId,
                            page.getPageId());

                    return List.of();
                }
                log.info(
                        "Facebook Ads request: pageId={}, adAccountId={}, actId={}",
                        page.getPageId(),
                        page.getAdAccountId(),
                        actId);

                /*
                 * Pass URI directly to RestTemplate.
                 *
                 * Do NOT use:
                 *
                 * restTemplate.getForObject(uri.toString(), ...)
                 *
                 * Use the URI overload.
                 */
                String response = restTemplate.getForObject(
                        uri,
                        String.class);

                log.debug(response);
                if (response == null || response.isBlank()) {

                    log.warn(
                            "Empty response from Facebook for page {}",
                            page.getPageId());

                    break;
                }

                JsonNode root = objectMapper.readTree(response);

                /*
                 * Facebook can return:
                 *
                 * {
                 * "error": {
                 * "message": "...",
                 * ...
                 * }
                 * }
                 */
                JsonNode error = root.get("error");

                if (error != null && !error.isNull()) {

                    log.error(
                            "Facebook API error for page {}: {}",
                            page.getPageId(),
                            error.path("message").asText(
                                    "Unknown Facebook API error"));

                    break;
                }

                JsonNode data = root.get("data");

                if (data != null && data.isArray()) {

                    for (JsonNode node : data) {

                        try {

                            campaigns.add(
                                    parseCampaign(
                                            node,
                                            page.getPageId()));

                        } catch (Exception e) {

                            log.warn(
                                    "Unable to parse campaign for page {}: {}",
                                    page.getPageId(),
                                    e.getMessage());
                        }
                    }
                }

                /*
                 * Handle Facebook pagination.
                 */
                JsonNode paging = root.get("paging");

                JsonNode cursors = paging != null
                        ? paging.get("cursors")
                        : null;

                after = cursors != null
                        && cursors.has("after")
                                ? cursors.get("after").asText()
                                : null;

            } while (after != null && !after.isBlank());

        } catch (Exception e) {

            log.error(
                    "Failed to fetch campaigns for page {}: {}",
                    page.getPageId(),
                    e.getMessage(),
                    e);
        }

        log.info(
                "Fetched {} campaigns for page {}",
                campaigns.size(),
                page.getPageId());

        return campaigns;
    }

    private RawCampaign parseCampaign(
            JsonNode node,
            String pageId) {

        JsonNode insights = node.path("insights").path("data");
        
        JsonNode firstInsight = insights.isArray() && !insights.isEmpty()
                ? insights.get(0)
                : null;

        long messageCount = 0L;
        BigDecimal costPerMessage = BigDecimal.ZERO;
        if (firstInsight != null) {
            /*
             * * --------------------------------------------------------- * MESSAGE COUNT *
             * --------------------------------------------------------- * * We use: * *
             * messaging_conversation_started_7d * * This represents messaging conversations
             * started * according to Meta's attribution window.
             */ 
            JsonNode actions = firstInsight.path("actions");
            
            if (actions.isArray()) {
                for (JsonNode action : actions) {
                    String actionType = action.path("action_type").asText();
                     
                    if ("onsite_conversion.messaging_conversation_started_7d".equals(actionType)) {
                        messageCount = action.path("value").asLong(0);
                        break;
                    }
                }
            }
            /*
             * * --------------------------------------------------------- * COST PER
             * MESSAGE * ---------------------------------------------------------
             */
            JsonNode costs = firstInsight.path("cost_per_action_type");
            if (costs.isArray()) {
                for (JsonNode cost : costs) {
                    String actionType = cost.path("action_type").asText();
                    if ("onsite_conversion.messaging_conversation_started_7d".equals(actionType)) {
                        log.info("actionType: {} ", actionType);
                        costPerMessage = parseAmount(cost.path("value").asText("0"));
                        log.info("count: {}",costPerMessage);
                        break;
                    }
                }
            }
        }

        String campaignName = escapeHtml(node.path("name")
                                .asText("Untitled"));
        log.info("count {} & cost per message {}", messageCount, costPerMessage);
        return RawCampaign.builder()

                .campaignId(
                        node.path("id").asText())

                .campaignName(campaignName)

                .status(
                        node.path("status")
                                .asText("UNKNOWN"))

                .objective(
                        node.hasNonNull("objective")
                                ? node.get("objective").asText()
                                : null)

                /*
                 * Facebook budget values are commonly represented
                 * in the account's smallest currency unit.
                 *
                 * Example:
                 * 1000 -> 10.00
                 */
                .dailyBudget(
                        node.hasNonNull("daily_budget")
                                ? parseCents(
                                        node.get("daily_budget")
                                                .asText())
                                : null)

                .lifetimeBudget(
                        node.hasNonNull("lifetime_budget")
                                ? parseCents(
                                        node.get("lifetime_budget")
                                                .asText())
                                : null)

                .startDate(
                        parseDate(
                                node.get("start_time")))

                .endDate(
                        parseDate(
                                node.get("stop_time")))

                /*
                 * Spend is already a currency amount.
                 *
                 * DO NOT use parseCents() here.
                 */
                .spend(
                        firstInsight != null
                                ? parseAmount(
                                        firstInsight
                                                .path("spend")
                                                .asText("0"))
                                : BigDecimal.ZERO)

                .impressions(
                        firstInsight != null
                                ? firstInsight
                                        .path("impressions")
                                        .asLong(0)
                                : 0L)

                .clicks(
                        firstInsight != null
                                ? firstInsight
                                        .path("clicks")
                                        .asLong(0)
                                : 0L)
                .messageCount(messageCount)
                .costPerMessage(costPerMessage)
                .pageId(pageId)

                .build();
    }

    /**
     * Converts a Facebook budget value from the smallest
     * currency unit into a decimal amount.
     *
     * Example:
     * 1000 -> 10.00
     */
    private BigDecimal parseCents(String value) {

        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {

            return new BigDecimal(value)
                    .divide(
                            new BigDecimal("100"),
                            2,
                            RoundingMode.HALF_UP);

        } catch (NumberFormatException e) {

            log.warn(
                    "Unable to parse budget value: {}",
                    value);

            return BigDecimal.ZERO;
        }
    }

    /**
     * Parses Facebook Insights monetary values.
     *
     * Spend is already represented as a decimal currency amount,
     * so there is no division by 100.
     */
    private BigDecimal parseAmount(String value) {

        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {

            return new BigDecimal(value)
                    .setScale(
                            2,
                            RoundingMode.HALF_UP);

        } catch (NumberFormatException e) {

            log.warn(
                    "Unable to parse spend value: {}",
                    value);

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

                return LocalDate.parse(
                        text.substring(0, 10),
                        DATE_FMT);
            }

        } catch (Exception e) {

            log.debug(
                    "Unable to parse Facebook date: {}",
                    dateNode);
        }

        return null;
    }

    @Builder
    @Value
    public static class RawCampaign {

        String campaignId;

        String campaignName;

        String status;

        String objective;

        BigDecimal dailyBudget;

        BigDecimal lifetimeBudget;

        LocalDate startDate;

        LocalDate endDate;

        BigDecimal spend;

        Long impressions;

        Long clicks;

        String pageId;

        Long messageCount;
        BigDecimal costPerMessage;
    }

    private boolean verifyAdAccountAccess(
            String actId,
            String accessToken,
            String pageId) {

        try {

            URI uri = UriComponentsBuilder
                    .fromUriString(
                            GRAPH_API_BASE + "/" + actId)
                    .queryParam(
                            "fields",
                            "id,name,account_status")
                    .queryParam(
                            "page_id",
                            pageId)
                    .queryParam(
                            "access_token",
                            accessToken)
                    .build()
                    .encode()
                    .toUri();

            log.info(
                    "Verifying Facebook ad account access for page {}: {}",
                    pageId,
                    actId);

            String response = restTemplate.getForObject(
                    uri,
                    String.class);

            log.info(
                    "Facebook ad account response for page {}: {}",
                    pageId,
                    response);

            return response != null
                    && !response.isBlank()
                    && !response.contains("\"error\"");

        } catch (Exception e) {

            log.error(
                    "Unable to access Facebook ad account {} for page {}: {}",
                    actId,
                    pageId,
                    e.getMessage());

            return false;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
