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

export function createSingleStudentSummary(data, scenarioName, runConfig, scenarioSpec) {
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

    const count409Duplicate = metricCount(data, 'single_student_409_duplicate');
    const count409Concurrency = metricCount(data, 'single_student_409_concurrency_conflict');
    const count409Other = metricCount(data, 'single_student_409_other');
    const count422ScheduleConflict = metricCount(data, 'single_student_422_schedule_conflict');
    const count422CreditLimit = metricCount(data, 'single_student_422_credit_limit');
    const count422Capacity = metricCount(data, 'single_student_422_capacity');
    const count422Other = metricCount(data, 'single_student_422_other');

    const durationMetric = data.metrics.enroll_latency_ms || {values: {}};
    const throughput = metricValue(data, 'enroll_attempts', 'rate');
    const allowedCodeRate = metricValue(data, 'enroll_allowed_code_rate', 'rate');
    const creditsWithinLimitRate = metricValue(data, 'single_student_credits_within_limit', 'rate');
    const timetableConflictFreeRate = metricValue(data, 'single_student_timetable_conflict_free', 'rate');

    const plannedAttemptsGauge = normalizedInt(metricGaugeValue(data, 'single_student_planned_attempts'));
    const plannedAttempts = plannedAttemptsGauge !== null ? plannedAttemptsGauge : runConfig.courseCount;
    const studentId = normalizedInt(metricGaugeValue(data, 'single_student_id'));
    const studentTotalCreditsFinal = normalizedInt(metricGaugeValue(data, 'single_student_total_credits_final'));
    const studentEnrolledCoursesFinal = normalizedInt(metricGaugeValue(data, 'single_student_enrolled_courses_final'));

    const allowedCodesOnly = allowedCodeRate === 1;
    const noUnexpectedStatus = countUnexpected === 0;
    const requestCountMatched = attempts === plannedAttempts;
    const creditsWithinLimit = metricExists(data, 'single_student_credits_within_limit')
        ? creditsWithinLimitRate === 1
        : true;
    const timetableConflictFree = metricExists(data, 'single_student_timetable_conflict_free')
        ? timetableConflictFreeRate === 1
        : true;
    const expectedScheduleConflictObserved = scenarioSpec.expectScheduleConflict
        ? count422ScheduleConflict > 0
        : true;
    const pass = allowedCodesOnly
        && noUnexpectedStatus
        && requestCountMatched
        && creditsWithinLimit
        && timetableConflictFree
        && expectedScheduleConflictObserved;

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
            selectionMode: scenarioSpec.selectionMode,
            plannedAttempts,
            courseCount: runConfig.courseCount,
        },
        student: {
            id: studentId,
            totalCreditsFinal: studentTotalCreditsFinal,
            enrolledCoursesFinal: studentEnrolledCoursesFinal,
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
        errorCodes: {
            duplicate409: count409Duplicate,
            concurrencyConflict409: count409Concurrency,
            other409: count409Other,
            scheduleConflict422: count422ScheduleConflict,
            creditLimit422: count422CreditLimit,
            capacity422: count422Capacity,
            other422: count422Other,
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
            requestCountMatched,
            creditsWithinLimit,
            timetableConflictFree,
            expectedScheduleConflictObserved,
            pass,
        },
    };

    const summaryPath = __ENV.SUMMARY_PATH || `performance/k6/results/single-student/${scenarioName}.summary.json`;
    const consoleLines = [
        `scenario=${scenarioName}`,
        `workload: mode=${summary.workload.selectionMode} plannedAttempts=${summary.workload.plannedAttempts} courseCount=${summary.workload.courseCount}`,
        `student: id=${summary.student.id} totalCreditsFinal=${summary.student.totalCreditsFinal} enrolledCoursesFinal=${summary.student.enrolledCoursesFinal}`,
        `total=${summary.totals.total} attempts=${summary.totals.attempts}/${plannedAttempts} 201=${count201} 409=${count409} 422=${count422} unexpected=${countUnexpected}`,
        `errorCodes: 409{duplicate=${count409Duplicate},concurrency=${count409Concurrency},other=${count409Other}} 422{schedule=${count422ScheduleConflict},credit=${count422CreditLimit},capacity=${count422Capacity},other=${count422Other}}`,
        `unexpectedByStatus=${JSON.stringify(unexpectedByStatus)}`,
        `successRate=${summary.ratios.successRate.toFixed(4)} conflictRate=${summary.ratios.conflictRate.toFixed(4)} ruleViolationRate=${summary.ratios.ruleViolationRate.toFixed(4)}`,
        `latency(ms): avg=${summary.latencyMs.avg.toFixed(2)} med=${summary.latencyMs.med.toFixed(2)} p95=${summary.latencyMs.p95.toFixed(2)} p99=${summary.latencyMs.p99.toFixed(2)} max=${summary.latencyMs.max.toFixed(2)}`,
        `throughput(req/s)=${summary.throughputRps.toFixed(2)}`,
        `assertions: allowed=${allowedCodesOnly} noUnexpected=${noUnexpectedStatus} reqMatched=${requestCountMatched} credits=${creditsWithinLimit} timetable=${timetableConflictFree} expectedScheduleConflict=${expectedScheduleConflictObserved} pass=${pass}`,
    ].join('\n');

    return {
        [summaryPath]: JSON.stringify(summary, null, 2),
        stdout: `${consoleLines}\n`,
    };
}
