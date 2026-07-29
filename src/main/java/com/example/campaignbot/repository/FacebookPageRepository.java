package com.example.campaignbot.repository;

import com.example.campaignbot.entity.FacebookPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacebookPageRepository extends JpaRepository<FacebookPage, Long> {

    Optional<FacebookPage> findByPageId(String pageId);

    List<FacebookPage> findByActiveTrue();
}
