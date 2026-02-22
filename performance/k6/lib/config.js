import { Counter, Gauge, Rate, Trend } from 'k6/metrics';

function intEnv(name, fallback) {
  const raw = __ENV[name];
  if (!raw) {
    return fallback;
  }

  const parsed = Number.parseInt(raw, 10);
  if (Number.isNaN(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }

  return parsed;
}

function boolEnv(name, fallback) {
  const raw = __ENV[name];
  if (!raw) {
    return fallback;
  }

  const normalized = raw.toLowerCase();
  if (normalized === '1' || normalized === 'true') {
    return true;
  }
  if (normalized === '0' || normalized === 'false') {
    return false;
  }

  throw new Error(`${name} must be true/false or 1/0`);
}

function nonNegativeIntEnv(name, fallback) {
  const raw = __ENV[name];
  if (!raw) {
    return fallback;
  }

  const parsed = Number.parseInt(raw, 10);
  if (Number.isNaN(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative integer`);
  }

  return parsed;
}

export function resolveRunConfig() {
  return {
    baseUrl: __ENV.BASE_URL || 'http://localhost:8080',
    vus: intEnv('VUS', 5000),
    loops: intEnv('LOOPS', 1),
    rampUpSeconds: nonNegativeIntEnv('RAMP_UP_SECONDS', 5),
    maxDuration: __ENV.MAX_DURATION || '2m',
    p95Ms: intEnv('P95_MS', 1000),
    failOnAssertion: boolEnv('FAIL_ON_ASSERTION', false),
  };
}

export function createOptions(runConfig) {
  const expectedAttempts = runConfig.vus * runConfig.loops;
  const thresholds = {
    enroll_latency_ms: [`p(95)<${runConfig.p95Ms}`],
    enroll_allowed_code_rate: ['rate==1'],
    enroll_status_unexpected: ['count==0'],
    enroll_attempts: [`count==${expectedAttempts}`],
  };

  if (runConfig.failOnAssertion) {
    thresholds.domain_capacity_not_exceeded = ['rate==1'];
  }

  return {
    scenarios: {
      hot_course: {
        executor: 'per-vu-iterations',
        vus: runConfig.vus,
        iterations: runConfig.loops,
        maxDuration: runConfig.maxDuration,
        gracefulStop: '0s',
      },
    },
    thresholds,
  };
}

export const status201 = new Counter('enroll_status_201');
export const status409 = new Counter('enroll_status_409');
export const status422 = new Counter('enroll_status_422');
export const statusUnexpected = new Counter('enroll_status_unexpected');
export const enrollAttempts = new Counter('enroll_attempts');
export const allowedCodeRate = new Rate('enroll_allowed_code_rate');
export const enrollLatency = new Trend('enroll_latency_ms', true);
export const hotCourseId = new Gauge('hot_course_id');
export const hotCourseCapacity = new Gauge('hot_course_capacity');
export const hotCourseEnrolledFinal = new Gauge('hot_course_enrolled_final');
export const domainCapacityNotExceeded = new Rate('domain_capacity_not_exceeded');
