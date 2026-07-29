package com.example.campaignbot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns", indexes = {
    @Index(name = "idx_campaigns_dates", columnList = "start_date, end_date"),
    @Index(name = "idx_campaigns_page", columnList = "page_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false, unique = true)
    private String campaignId;

    @Column(name = "campaign_name", nullable = false)
    private String campaignName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", referencedColumnName = "page_id", nullable = false)
    private FacebookPage page;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "objective")
    private String objective;

    @Column(name = "daily_budget")
    private BigDecimal dailyBudget;

    @Column(name = "lifetime_budget")
    private BigDecimal lifetimeBudget;

    @Column(name = "spend", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal spend = BigDecimal.ZERO;

    @Column(name = "impressions")
    @Builder.Default
    private Long impressions = 0L;

    @Column(name = "clicks")
    @Builder.Default
    private Long clicks = 0L;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "message_count")
    @Builder.Default
    private Long messageCount = 0L;

    @Column(name = "cost_per_message", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal costPerMessage = BigDecimal.ZERO;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
