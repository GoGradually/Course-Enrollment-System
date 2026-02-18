package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class EnrollmentStrategyRouter {

    private final Map<EnrollmentStrategyType, EnrollmentStrategy> strategiesByType;

    public EnrollmentStrategyRouter(List<EnrollmentStrategy> strategies) {
        EnumMap<EnrollmentStrategyType, EnrollmentStrategy> map = new EnumMap<>(EnrollmentStrategyType.class);

        for (EnrollmentStrategy strategy : strategies) {
            EnrollmentStrategy previous = map.put(strategy.type(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Duplicate enrollment strategy type: " + strategy.type());
            }
        }

        for (EnrollmentStrategyType type : EnrollmentStrategyType.values()) {
            if (!map.containsKey(type)) {
                throw new IllegalStateException("Missing enrollment strategy for type: " + type);
            }
        }

        this.strategiesByType = Map.copyOf(map);
    }

    public EnrollmentStrategy get(EnrollmentStrategyType type) {
        EnrollmentStrategy strategy = strategiesByType.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown enrollment strategy type: " + type);
        }
        return strategy;
    }
}
