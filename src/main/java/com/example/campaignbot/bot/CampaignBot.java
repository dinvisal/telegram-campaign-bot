package com.example.campaignbot.bot;

import com.example.campaignbot.entity.Campaign;
import com.example.campaignbot.entity.FacebookPage;
import com.example.campaignbot.service.CampaignService;
import com.example.campaignbot.service.TelegramMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CampaignBot
        implements SpringLongPollingBot,
        LongPollingSingleThreadUpdateConsumer {

    private final CampaignService campaignService;
    private final TelegramMessageService telegramMessageService;
    private final String botToken;

    public CampaignBot(
            CampaignService campaignService,
            TelegramMessageService telegramMessageService,
            @Value("${telegram.bot.token}") String botToken) {

        this.campaignService = campaignService;
        this.telegramMessageService = telegramMessageService;
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

            Map<FacebookPage, List<Campaign>> grouped = campaignService.getTodaysActiveCampaigns();

            if (grouped == null || grouped.isEmpty()) {

                sendMessage(
                        chatId,
                        "📊 Today's Active Campaigns\n\n"
                                + "No active campaigns found for today.");

                return;
            }

            StringBuilder sb = new StringBuilder();

            sb.append("📊 TODAY'S ACTIVE CAMPAIGNS\n\n");

            int totalCampaigns = 0;

            long totalMessages = 0L;
            BigDecimal totalSpend = BigDecimal.ZERO;

            for (Map.Entry<FacebookPage, List<Campaign>> entry : grouped.entrySet()) {

                FacebookPage page = entry.getKey();

                List<Campaign> campaigns = entry.getValue();

                if (campaigns == null || campaigns.isEmpty()) {
                    continue;
                }

                sb.append("📄 ")
                        .append(page.getAdAccountId())
                        .append("\n");

                /* * Telegram monospace table. */ 
                sb.append("<pre>");
                sb.append(String.format("%-28s %6s %5s %5s%n", "Campaign", "Msgs", "Cost/Msg", "Total"));
                sb.append("────────────────\n");

                for (Campaign campaign : campaigns) {

                    if (campaign == null) {
                        continue;
                    }

                    String campaignName = escapeHtml(campaign.getCampaignName() != null ? campaign.getCampaignName() : "Untitled");

                    /* * Keep the table readable if campaign names are very long. */
                    if (campaignName.length() > 28) {
                        campaignName = campaignName.substring(0, 25) + "...";
                    }

                    long messageCount = campaign.getMessageCount() != null ? campaign.getMessageCount() : 0L;
                    BigDecimal costPerMessage = campaign.getCostPerMessage() != null ? campaign.getCostPerMessage()
                            : BigDecimal.ZERO;
                    BigDecimal spend = campaign.getSpend() != null ? campaign.getSpend() : BigDecimal.ZERO;

                    /* * Add row. */
                    sb.append(String.format("🟢 %-25s %6d %5s %5s%n", campaignName, messageCount,
                            "$" + formatAmount(costPerMessage), "$" + formatAmount(spend)));
                    /* * Summary totals. */
                    totalCampaigns++;
                    totalMessages += messageCount;
                    totalSpend = totalSpend.add(spend);
                }
                sb.append("</pre>\n");
            }

            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("📈 SUMMARY\n\n");
            sb.append("Campaigns: ").append(totalCampaigns).append("\n");
            sb.append("Ads Accounts: ").append(grouped.size()).append("\n");
            sb.append("Messages: ").append(totalMessages).append("\n");
            sb.append("Total Spend: $").append(formatAmount(totalSpend)).append("\n");
            sendMessage(chatId, sb.toString());

        } catch (

        Exception e) {

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

    private String formatAmount(BigDecimal amount) {

        if (amount == null) {
            return "0";
        }

        return amount
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private void sendMessage(
            long chatId,
            String text) {

        telegramMessageService.sendMessage(
                chatId,
                text);
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