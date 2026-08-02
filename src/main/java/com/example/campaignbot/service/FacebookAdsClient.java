package com.example.campaignbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Low-level HTTP client for the Facebook Marketing Graph API.
 * Handles URI construction and raw HTTP calls only — no JSON parsing.
 */
@Slf4j
@Component
public class FacebookAdsClient {

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v19.0";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestTemplate restTemplate;

    public FacebookAdsClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch one page of campaign data as raw JSON.
     */
    public String fetchCampaignPage(
            String actId,
            String accessToken,
            String afterCursor) {

        LocalDate today = LocalDate.now();
        String todayString = today.format(DATE_FMT);

        String fields = "id,name,status,effective_status,objective,"
                + "daily_budget,lifetime_budget,"
                + "start_time,stop_time,"
                + "insights.time_range("
                + "{\"since\":\"" + todayString + "\","
                + "\"until\":\"" + todayString + "\"}"
                + "){spend,impressions,clicks,"
                + "actions,cost_per_action_type}";

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(GRAPH_API_BASE + "/" + actId + "/campaigns")
                .queryParam("fields", fields)
                .queryParam("filtering",
                        "[{\"field\":\"effective_status\","
                                + "\"operator\":\"IN\","
                                + "\"value\":[\"ACTIVE\"]}]")
                .queryParam("limit", 100)
                .queryParam("access_token", accessToken);

        if (afterCursor != null && !afterCursor.isBlank()) {
            builder.queryParam("after", afterCursor);
        }

        URI uri = builder.build().toUri();

        log.debug("Facebook campaign request URI: {}", uri.toASCIIString());

        return restTemplate.getForObject(uri, String.class);
    }

    /**
     * Verify that the access token has permission for the given ad account.
     */
    public boolean verifyAdAccountAccess(
            String actId,
            String accessToken,
            String pageId) {

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(GRAPH_API_BASE + "/" + actId)
                    .queryParam("fields", "id,name,account_status")
                    .queryParam("page_id", pageId)
                    .queryParam("access_token", accessToken)
                    .build()
                    .encode()
                    .toUri();

            log.info("Verifying Facebook ad account access for page {}: {}", pageId, actId);

            String response = restTemplate.getForObject(uri, String.class);

            log.info("Facebook ad account response for page {}: {}", pageId, response);

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
}
