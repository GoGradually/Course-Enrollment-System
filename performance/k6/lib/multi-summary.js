function metricCount(data, metricName) {
    const metric = data.metrics[metricName];
    if (!metric || !metric.values || typeof metric.values.count !== 'number') {
        return 0;
    }
    return metric.values.count;
}

function metricValue(data, metricName, fieldName) {
    const metric = data.metrics[metricName];
    if (!metric || !metric.values || typeof metric.values[fieldName] !== 'number') {
        return 0;
    }
    return metric.values[fieldName];
}

function metricGaugeValue(data, metricName) {
    const metric = data.metrics[metricName];
    if (!metric || !metric.values) {
        return null;
    }

    if (typeof metric.values.value === 'number') {
        return metric.values.value;
    }

    if (typeof metric.values.avg === 'number') {
        return metric.values.avg;
    }

    if (typeof metric.values.max === 'number') {
        return metric.values.max;
    }

    return null;
}

function metricExists(data, metricName) {
    const metric = data.metrics[metricName];
    return Boolean(metric && metric.values);
}

function formatRate(value, total) {
    if (total <= 0) {
        return 0;
    }
    return value / total;
}

function normalizedInt(value) {
    if (value === null) {
        return null;
    }
    return Math.round(value);
}

export function createMultiSummary(data, scenarioName, runConfig) {
    const count201 = metricCount(data, 'enroll_status_201');
    const count409 = metricCount(data, 'enroll_status_409');
    const count422 = metricCount(data, 'enroll_status_422');
    const countUnexpected = metricCount(data, 'enroll_status_unexpected');
    const countUnexpected400 = metricCount(data, 'enroll_status_unexpected_400');
    const countUnexpected404 = metricCount(data, 'enroll_status_unexpected_404');
    const countUnexpected500 = metricCount(data, 'enroll_status_unexpected_500');
    const countUnexpectedOther = metricCount(data, 'enroll_status_unexpected_other');
    const attempts = metricCount(data, 'enroll_attempts');
    const total = count201 + count409 + count422 + countUnexpected;

    const durationMetric = data.metrics.enroll_latency_ms || {values: {}};
    const throughput = metricValue(data, 'enroll_attempts', 'rate');
    const allowedCodeRate = metricValue(data, 'enroll_allowed_code_rate', 'rate');
    const coursesWithinCapacityRate = metricValue(data, 'domain_courses_within_capacity', 'rate');

    const workloadCourseCount = normalizedInt(metricGaugeValue(data, 'multi_course_count'));
    const plannedAttempts = normalizedInt(metricGaugeValue(data, 'multi_planned_attempts'));
    const maxConfiguredAttemptsGauge = normalizedInt(metricGaugeValue(data, 'multi_max_configured_attempts'));
    const totalCapacity = normalizedInt(metricGaugeValue(data, 'multi_total_capacity'));
    const totalEnrolledFinal = normalizedInt(metricGaugeValue(data, 'multi_total_enrolled_final'));

    const allowedCodesOnly = allowedCodeRate === 1;
    const noUnexpectedStatus = countUnexpected === 0;
    const attemptsMatchedPlan = plannedAttempts !== null && attempts === plannedAttempts;
    const allCoursesWithinCapacity = metricExists(data, 'domain_courses_within_capacity')
        ? coursesWithinCapacityRate === 1
        : true;
    const pass = allowedCodesOnly && noUnexpectedStatus && attemptsMatchedPlan && allCoursesWithinCapacity;

    const unexpectedByStatus = {};
    if (countUnexpected400 > 0) {
        unexpectedByStatus['400'] = countUnexpected400;
    }
    if (countUnexpected404 > 0) {
        unexpectedByStatus['404'] = countUnexpected404;
    }
    if (countUnexpected500 > 0) {
        unexpectedByStatus['500'] = countUnexpected500;
    }
    if (countUnexpectedOther > 0) {
        unexpectedByStatus.other = countUnexpectedOther;
    }

    const summary = {
        scenario: scenarioName,
        workload: {
            courseCount: workloadCourseCount,
            competitionMultiplier: runConfig.competitionMultiplier,
            plannedAttempts,
            maxConfiguredAttempts: maxConfiguredAttemptsGauge !== null
                ? maxConfiguredAttemptsGauge
                : runConfig.maxTotalAttempts,
            totalCapacity,
            totalEnrolledFinal,
        },
        totals: {
            total,
            attempts,
            status201: count201,
            status409: count409,
            status422: count422,
            unexpected: countUnexpected,
            unexpectedByStatus,
        },
        ratios: {
            successRate: formatRate(count201, total),
            conflictRate: formatRate(count409, total),
            ruleViolationRate: formatRate(count422, total),
        },
        latencyMs: {
            avg: durationMetric.values.avg || 0,
            med: durationMetric.values.med || 0,
            max: durationMetric.values.max || 0,
            p95: durationMetric.values['p(95)'] || 0,
            p99: durationMetric.values['p(99)'] || 0,
        },
        throughputRps: throughput,
        domainAssertions: {
            allowedCodesOnly,
            noUnexpectedStatus,
            attemptsMatchedPlan,
            allCoursesWithinCapacity,
            pass,
        },
    };

    const summaryPath = __ENV.SUMMARY_PATH || `performance/k6/results/multi/${scenarioName}.summary.json`;
    const consoleLines = [
        `scenario=${scenarioName}`,
        `workload: courses=${summary.workload.courseCount} multiplier=${summary.workload.competitionMultiplier} plannedAttempts=${summary.workload.plannedAttempts} maxConfiguredAttempts=${summary.workload.maxConfiguredAttempts} totalCapacity=${summary.workload.totalCapacity} totalEnrolledFinal=${summary.workload.totalEnrolledFinal}`,
        `total=${summary.totals.total} attempts=${summary.totals.attempts} 201=${count201} 409=${count409} 422=${count422} unexpected=${countUnexpected}`,
        `unexpectedByStatus=${JSON.stringify(unexpectedByStatus)}`,
        `successRate=${summary.ratios.successRate.toFixed(4)} conflictRate=${summary.ratios.conflictRate.toFixed(4)} ruleViolationRate=${summary.ratios.ruleViolationRate.toFixed(4)}`,
        `latency(ms): avg=${summary.latencyMs.avg.toFixed(2)} med=${summary.latencyMs.med.toFixed(2)} p95=${summary.latencyMs.p95.toFixed(2)} p99=${summary.latencyMs.p99.toFixed(2)} max=${summary.latencyMs.max.toFixed(2)}`,
        `throughput(req/s)=${summary.throughputRps.toFixed(2)}`,
        `assertions: allowed=${allowedCodesOnly} noUnexpected=${noUnexpectedStatus} attemptsMatchedPlan=${attemptsMatchedPlan} allCoursesWithinCapacity=${allCoursesWithinCapacity} pass=${pass}`,
    ].join('\n');

    return {
        [summaryPath]: JSON.stringify(summary, null, 2),
        stdout: `${consoleLines}\n`,
    };
}
