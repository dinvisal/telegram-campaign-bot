package com.example.campaignbot.repository;

import com.example.campaignbot.entity.Campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import java.util.Optional;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByPage_PageId(String pageId);

    @Query("""
        SELECT c FROM Campaign c
        JOIN FETCH c.page
        WHERE c.status = 'ACTIVE'
          AND COALESCE(c.spend, 0) > 0
          AND c.startDate <= :today
          AND (c.endDate IS NULL OR c.endDate >= :today)
        ORDER BY c.page.pageName, c.campaignName
    """)
    List<Campaign> findActiveCampaignsForDate(@Param("today") LocalDate today);

    boolean existsByCampaignId(String campaignId);

    Optional<Campaign> findByCampaignId(String campaignId);

    @Modifying
    @Query("""
        UPDATE Campaign c
        SET c.status = 'INACTIVE'
        WHERE c.page.pageId = :pageId
    """)
    void markInactiveByPageId(@Param("pageId") String pageId);
}
