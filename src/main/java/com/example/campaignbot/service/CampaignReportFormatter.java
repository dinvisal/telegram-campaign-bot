package com.example.campaignbot.service;

import com.example.campaignbot.entity.Campaign;
import com.example.campaignbot.entity.FacebookPage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Builds Telegram-formatted campaign report strings.
 * Stateless and independently testable.
 */
@Component
public class CampaignReportFormatter {

    public String formatTodayReport(Map<FacebookPage, List<Campaign>> grouped) {

        if (grouped == null || grouped.isEmpty()) {
            return "📊 Today's Active Campaigns\n\n"
                    + "No active campaigns found for today.";
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

            sb.append("📄 ").append(escapeHtml(page.getAdAccountId())).append("\n");

            sb.append("<pre>");
            sb.append(String.format("%-28s %6s %5s %5s%n",
                    "Campaign", "Msgs", "Cost/Msg", "Total"));
            sb.append("────────────────\n");

            for (Campaign campaign : campaigns) {
                if (campaign == null) {
                    continue;
                }

                String campaignName = escapeHtml(
                        campaign.getCampaignName() != null ? campaign.getCampaignName() : "Untitled");

                if (campaignName.length() > 28) {
                    campaignName = campaignName.substring(0, 25) + "...";
                }

                long messageCount = campaign.getMessageCount() != null
                        ? campaign.getMessageCount() : 0L;
                BigDecimal costPerMessage = campaign.getCostPerMessage() != null
                        ? campaign.getCostPerMessage() : BigDecimal.ZERO;
                BigDecimal spend = campaign.getSpend() != null
                        ? campaign.getSpend() : BigDecimal.ZERO;

                sb.append(String.format("🟢 %-25s %6d %5s %5s%n",
                        campaignName,
                        messageCount,
                        "$" + formatAmount(costPerMessage),
                        "$" + formatAmount(spend)));

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

        return sb.toString();
    }

    String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return amount
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
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
