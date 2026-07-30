package com.example.campaignbot.scheduler;

import com.example.campaignbot.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignSyncScheduler {

    private final CampaignService campaignService;

    /**
     * Sync campaigns from Facebook every 30 minutes during business hours.
     * Cron: every 30 minutes from 06:00 to 23:30.
     */
    //@Scheduled(cron = "0 */30 6-23 * * *", zone = "Asia/Phnom_Penh")
    public void syncCampaigns() {
        log.info("Starting scheduled campaign sync...");
        campaignService.syncAllPages();
        log.info("Scheduled campaign sync completed.");
    }
}
