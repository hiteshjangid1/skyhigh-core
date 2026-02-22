package com.skyhigh.infrastructure.persistence.abuse;

import com.skyhigh.domain.abuse.entity.AbuseEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbuseEventRepository extends JpaRepository<AbuseEvent, Long> {
}
