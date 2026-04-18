package com.acheao.languageagent.v2.repository;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.SourceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceItemRepository extends JpaRepository<SourceItem, UUID> {
    List<SourceItem> findAllByUserOrderByCreatedAtDesc(User user);
}
