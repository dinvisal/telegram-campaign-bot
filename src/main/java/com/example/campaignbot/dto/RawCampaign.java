package com.example.campaignbot.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record RawCampaign(
        String campaignId,
        String campaignName,
        String status,
        String effectiveStatus,
        String objective,
        BigDecimal dailyBudget,
        BigDecimal lifetimeBudget,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal spend,
        Long impressions,
        Long clicks,
        String pageId,
        Long messageCount,
        BigDecimal costPerMessage) {
}
