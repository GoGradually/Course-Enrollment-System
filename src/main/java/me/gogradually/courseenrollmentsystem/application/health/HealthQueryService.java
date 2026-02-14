package me.gogradually.courseenrollmentsystem.application.health;

import me.gogradually.courseenrollmentsystem.domain.health.HealthStatus;
import org.springframework.stereotype.Service;

@Service
public class HealthQueryService {

    /**
     * Returns application health status for readiness checks.
     */
    public String getCurrentStatus() {
        return HealthStatus.UP.name();
    }
}
