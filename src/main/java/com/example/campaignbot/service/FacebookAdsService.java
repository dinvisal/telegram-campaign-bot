package com.example.campaignbot.service;

import com.example.campaignbot.dto.RawCampaign;
import com.example.campaignbot.entity.FacebookPage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates Facebook campaign data fetching.
 * Delegates HTTP to {@link FacebookAdsClient} and JSON parsing to {@link CampaignParser}.
 */
@Slf4j
@Service
public class FacebookAdsService {

    private final FacebookAdsClient adsClient;
    private final CampaignParser parser;
    private final ObjectMapper objectMapper;

    public FacebookAdsService(
            FacebookAdsClient adsClient,
            CampaignParser parser,
            ObjectMapper objectMapper) {

        this.adsClient = adsClient;
        this.parser = parser;
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

        if (page.getAdAccountId() == null || page.getAdAccountId().isBlank()) {
            log.warn("No ad account configured for page {}", page.getPageId());
            return List.of();
        }

        if (page.getAccessToken() == null || page.getAccessToken().isBlank()) {
            log.warn("No access token configured for page {}", page.getPageId());
            return List.of();
        }

        String actId = page.getAdAccountId().startsWith("act_")
                ? page.getAdAccountId()
                : "act_" + page.getAdAccountId();

        if (!adsClient.verifyAdAccountAccess(actId, page.getAccessToken(), page.getPageId())) {
            log.error("Facebook ad account {} is not accessible for page {}", actId, page.getPageId());
            return List.of();
        }

        List<RawCampaign> campaigns = new ArrayList<>();
        String after = null;

        try {
            do {
                String response = adsClient.fetchCampaignPage(
                        actId,
                        page.getAccessToken(),
                        after);

                if (response == null || response.isBlank()) {
                    log.warn("Empty response from Facebook for page {}", page.getPageId());
                    break;
                }

                JsonNode root = objectMapper.readTree(response);

                JsonNode error = root.get("error");
                if (error != null && !error.isNull()) {
                    log.error("Facebook API error for page {}: {}",
                            page.getPageId(),
                            error.path("message").asText("Unknown Facebook API error"));
                    break;
                }

                JsonNode data = root.get("data");
                if (data != null && data.isArray()) {
                    for (JsonNode node : data) {
                        try {
                            campaigns.add(parser.parse(node, page.getPageId()));
                        } catch (Exception e) {
                            log.warn("Unable to parse campaign for page {}: {}",
                                    page.getPageId(), e.getMessage());
                        }
                    }
                }

                after = extractAfterCursor(root);

            } while (after != null && !after.isBlank());

        } catch (Exception e) {
            log.error("Failed to fetch campaigns for page {}: {}",
                    page.getPageId(), e.getMessage(), e);
        }

        log.info("Fetched {} campaigns for page {}", campaigns.size(), page.getPageId());
        return campaigns;
    }

    private static String extractAfterCursor(JsonNode root) {
        JsonNode paging = root.get("paging");
        JsonNode cursors = paging != null ? paging.get("cursors") : null;
        return cursors != null && cursors.has("after")
                ? cursors.get("after").asText()
                : null;
    }
}
