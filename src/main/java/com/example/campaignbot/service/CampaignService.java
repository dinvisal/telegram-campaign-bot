package com.example.campaignbot.service;

import com.example.campaignbot.dto.RawCampaign;
import com.example.campaignbot.entity.Campaign;
import com.example.campaignbot.entity.FacebookPage;
import com.example.campaignbot.repository.CampaignRepository;
import com.example.campaignbot.repository.FacebookPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final FacebookPageRepository pageRepository;
    private final FacebookAdsService facebookAdsService;

    /**
     * Returns all campaigns that are active today, grouped by page.
     */
    @Transactional(readOnly = true)
    public Map<FacebookPage, List<Campaign>> getTodaysActiveCampaigns() {
        LocalDate today = LocalDate.now();
        List<Campaign> campaigns = campaignRepository.findActiveCampaignsForDate(today);

        Map<FacebookPage, List<Campaign>> grouped = new LinkedHashMap<>();
        for (Campaign c : campaigns) {
            grouped.computeIfAbsent(c.getPage(), k -> new ArrayList<>()).add(c);
        }
        return grouped;
    }

    /**
     * Sync campaigns from Facebook API for all active pages.
     */
    @Transactional
    public void syncAllPages() {

        List<FacebookPage> pages = pageRepository.findByActiveTrue();
        log.info("Syncing campaigns for {} pages", pages.size());

        for (FacebookPage page : pages) {
            try {
                log.debug("Page: {}", page.getPageId());
                syncPage(page);
            } catch (Exception e) {
                log.error("Sync failed for page {}: {}", page.getPageName(), e.getMessage());
            }
        }
    }

    @Transactional
    public void syncPage(FacebookPage page) {
        // Mark all existing campaigns for this page inactive.
        campaignRepository.markInactiveByPageId(page.getPageId());

        List<RawCampaign> rawCampaigns = facebookAdsService.fetchCampaigns(page);
        log.info("Fetched {} campaigns for page {}", rawCampaigns.size(), page.getPageName());

        List<Campaign> campaignsToSave = new ArrayList<>();

        for (RawCampaign raw : rawCampaigns) {
            if (raw == null || raw.campaignId() == null || raw.campaignId().isBlank()) {
                log.warn("Skipping campaign with missing campaign id for page {}", page.getPageId());
                continue;
            }
            log.debug("id: {}", raw.campaignId());

            Campaign campaign = campaignRepository
                    .findByCampaignId(raw.campaignId())
                    .orElse(null);

            if (campaign == null) {
                campaign = Campaign.builder()
                        .campaignId(raw.campaignId())
                        .page(page)
                        .build();
            }

            log.info("campaign id: {} status {} effective_status {}", raw.campaignId(), raw.status(), raw.effectiveStatus());
            campaign.setCampaignId(raw.campaignId());
            campaign.setCampaignName(raw.campaignName());
            campaign.setStatus(raw.status());
            campaign.setObjective(raw.objective());
            campaign.setDailyBudget(raw.dailyBudget());
            campaign.setLifetimeBudget(raw.lifetimeBudget());
            campaign.setStartDate(raw.startDate());
            campaign.setEndDate(raw.endDate());
            campaign.setSpend(raw.spend());
            campaign.setImpressions(raw.impressions());
            campaign.setClicks(raw.clicks());
            campaign.setLastSyncedAt(LocalDateTime.now());
            campaign.setMessageCount(raw.messageCount());
            campaign.setCostPerMessage(raw.costPerMessage());
            campaign.setPage(page);

            campaignsToSave.add(campaign);
        }
        if (!campaignsToSave.isEmpty()) {
            campaignRepository.saveAll(campaignsToSave);
        }

        log.info("Saved {} campaigns for page {}",
                campaignsToSave.size(),
                page.getPageName());

        log.debug("Sync complete for page {}", page.getPageName());
    }
}
