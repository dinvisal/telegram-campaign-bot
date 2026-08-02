package com.example.campaignbot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @Builder.Default
    @Column(name = "spend", precision = 12, scale = 2)
    private BigDecimal spend = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "impressions")
    private Long impressions = 0L;

    @Builder.Default
    @Column(name = "clicks")
    private Long clicks = 0L;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "message_count")
    private Long messageCount = 0L;

    @Builder.Default
    @Column(name = "cost_per_message", precision = 19, scale = 2)
    private BigDecimal costPerMessage = BigDecimal.ZERO;
}