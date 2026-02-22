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

export function createSummary(data, scenarioName, runConfig) {
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

  const durationMetric = data.metrics.enroll_latency_ms || { values: {} };
  const throughput = metricValue(data, 'enroll_attempts', 'rate');
  const expectedAttempts = runConfig.vus * runConfig.loops;

  const hotCourseId = metricGaugeValue(data, 'hot_course_id');
  const hotCourseCapacity = metricGaugeValue(data, 'hot_course_capacity');
  const hotCourseEnrolledFinal = metricGaugeValue(data, 'hot_course_enrolled_final');

  const allowedCodeRate = metricValue(data, 'enroll_allowed_code_rate', 'rate');
  const domainCapacityRate = metricValue(data, 'domain_capacity_not_exceeded', 'rate');
  const allowedCodesOnly = allowedCodeRate === 1;
  const noUnexpectedStatus = countUnexpected === 0;
  const requestCountMatched = attempts === expectedAttempts;

  const capacityFromStatus = hotCourseCapacity === null
    ? true
    : count201 <= hotCourseCapacity;
  const capacityFromFinalState = metricExists(data, 'domain_capacity_not_exceeded')
    ? domainCapacityRate === 1
    : true;
  const capacityNotExceeded = capacityFromStatus && capacityFromFinalState;
  const pass = allowedCodesOnly && noUnexpectedStatus && requestCountMatched && capacityNotExceeded;

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
    course: {
      id: hotCourseId,
      capacity: hotCourseCapacity,
      enrolledFinal: hotCourseEnrolledFinal,
    },
    totals: {
      total,
      attempts,
      expectedAttempts,
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
      requestCountMatched,
      capacityNotExceeded,
      pass,
    },
  };

  const summaryPath = __ENV.SUMMARY_PATH || `performance/k6/results/${scenarioName}.summary.json`;
  const consoleLines = [
    `scenario=${scenarioName}`,
    `total=${summary.totals.total} attempts=${attempts}/${expectedAttempts} 201=${count201} 409=${count409} 422=${count422} unexpected=${countUnexpected}`,
    `unexpectedByStatus=${JSON.stringify(unexpectedByStatus)}`,
    `successRate=${summary.ratios.successRate.toFixed(4)} conflictRate=${summary.ratios.conflictRate.toFixed(4)} ruleViolationRate=${summary.ratios.ruleViolationRate.toFixed(4)}`,
    `latency(ms): avg=${summary.latencyMs.avg.toFixed(2)} med=${summary.latencyMs.med.toFixed(2)} p95=${summary.latencyMs.p95.toFixed(2)} p99=${summary.latencyMs.p99.toFixed(2)} max=${summary.latencyMs.max.toFixed(2)}`,
    `throughput(req/s)=${summary.throughputRps.toFixed(2)}`,
    `course: id=${hotCourseId} capacity=${hotCourseCapacity} enrolledFinal=${hotCourseEnrolledFinal}`,
    `assertions: allowed=${allowedCodesOnly} noUnexpected=${noUnexpectedStatus} reqMatched=${requestCountMatched} capacity=${capacityNotExceeded} pass=${pass}`,
  ].join('\n');

  return {
    [summaryPath]: JSON.stringify(summary, null, 2),
    stdout: `${consoleLines}\n`,
  };
}
