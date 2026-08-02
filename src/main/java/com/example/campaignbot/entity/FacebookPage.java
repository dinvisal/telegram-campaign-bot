package com.example.campaignbot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "facebook_pages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacebookPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false, unique = true)
    private String pageId;

    @Column(name = "page_name", nullable = false)
    private String pageName;

    @Column(name = "access_token", nullable = false, length = 512)
    private String accessToken;

    @Column(name = "ad_account_id")
    private String adAccountId;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}