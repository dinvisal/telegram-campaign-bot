package com.example.campaignbot.bot;

import com.example.campaignbot.service.CampaignReportFormatter;
import com.example.campaignbot.service.CampaignService;
import com.example.campaignbot.service.TelegramMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@Component
public class CampaignBot
        implements SpringLongPollingBot,
        LongPollingSingleThreadUpdateConsumer {

    private final CampaignService campaignService;
    private final TelegramMessageService telegramMessageService;
    private final CampaignReportFormatter reportFormatter;
    private final String botToken;

    public CampaignBot(
            CampaignService campaignService,
            TelegramMessageService telegramMessageService,
            CampaignReportFormatter reportFormatter,
            @Value("${telegram.bot.token}") String botToken) {

        this.campaignService = campaignService;
        this.telegramMessageService = telegramMessageService;
        this.reportFormatter = reportFormatter;
        this.botToken = botToken;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {

        if (!update.hasMessage()
                || !update.getMessage().hasText()) {
            return;
        }

        String text = update.getMessage()
                .getText()
                .trim();

        long chatId = update.getMessage().getChatId();

        // Support commands such as:
        // /today
        // /today@my_campaign_bot
        String command = text
                .split("\\s+")[0]
                .split("@")[0]
                .toLowerCase();

        switch (command) {

            case "/start" -> handleStart(chatId);

            case "/today" -> handleToday(chatId);

            case "/sync" -> handleSync(chatId);

            case "/help" -> handleHelp(chatId);

            default -> sendMessage(
                    chatId,
                    "Unknown command.\n\n"
                            + "Use /help to see available commands.");
        }
    }

    private void handleStart(long chatId) {

        sendMessage(chatId, """
                📊 Welcome to Campaign Bot!

                Available commands:

                /today - Show all campaigns active today
                /sync  - Trigger a manual Facebook sync
                /help  - Show this help message
                """);
    }

    private void handleHelp(long chatId) {

        sendMessage(chatId, """
                📊 Campaign Bot Help

                /today
                Show all active campaigns for today,
                grouped by Facebook Page.

                /sync
                Manually synchronize campaign data
                from Facebook Ads.

                /help
                Show this help message.
                """);
    }

    private void handleToday(long chatId) {

        log.info("Processing /today for chatId={}", chatId);

        try {
            String report = reportFormatter.formatTodayReport(
                    campaignService.getTodaysActiveCampaigns());
            sendMessage(chatId, report);

        } catch (Exception e) {

            log.error(
                    "Error handling /today for chatId={}",
                    chatId,
                    e);

            sendMessage(
                    chatId,
                    "❌ Sorry, something went wrong "
                            + "while fetching campaigns.\n\n"
                            + "Please try again.");
        }
    }

    private void handleSync(long chatId) {

        log.info("Manual campaign sync requested by chatId={}", chatId);

        sendMessage(
                chatId,
                "🔄 Syncing campaigns from Facebook...\n\n"
                        + "This may take a moment.");

        try {

            campaignService.syncAllPages();

            sendMessage(
                    chatId,
                    "✅ Sync completed successfully!\n\n"
                            + "Use /today to see active campaigns.");

        } catch (Exception e) {

            log.error(
                    "Manual campaign sync failed for chatId={}",
                    chatId,
                    e);

            sendMessage(
                    chatId,
                    "❌ Sync failed.\n\n"
                            + "Please check the application logs.");
        }
    }

    private void sendMessage(
            long chatId,
            String text) {

        telegramMessageService.sendMessage(
                chatId,
                text);
    }
}
