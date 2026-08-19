package com.example.campaignbot.scheduler;

import com.example.campaignbot.service.CampaignReportFormatter;
import com.example.campaignbot.service.CampaignService;
import com.example.campaignbot.service.TelegramMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CampaignReportScheduler {

    private final CampaignService campaignService;
    private final CampaignReportFormatter reportFormatter;
    private final TelegramMessageService telegramMessageService;
    private final long reportChatId;

    public CampaignReportScheduler(
            CampaignService campaignService,
            CampaignReportFormatter reportFormatter,
            TelegramMessageService telegramMessageService,
            @Value("${telegram.report.chat-id}") long reportChatId) {

        this.campaignService = campaignService;
        this.reportFormatter = reportFormatter;
        this.telegramMessageService = telegramMessageService;
        this.reportChatId = reportChatId;
    }

    /**
     * Send the daily campaign report (same content as /today) to the admin
     * chat at 08:00 and 21:00 local time (Asia/Phnom_Penh).
     */
    @Scheduled(cron = "0 0 8,21 * * *", zone = "Asia/Phnom_Penh")
    public void sendDailyReport() {

        log.info("Sending scheduled campaign report to chatId={}", reportChatId);

        try {
            String report = reportFormatter.formatTodayReport(
                    campaignService.getTodaysActiveCampaigns());
            telegramMessageService.sendMessage(reportChatId, report);

        } catch (Exception e) {
            log.error(
                    "Failed to send scheduled campaign report to chatId={}",
                    reportChatId,
                    e);
        }
    }
}
