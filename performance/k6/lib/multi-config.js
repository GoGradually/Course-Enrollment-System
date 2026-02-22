import {Counter, Gauge, Rate, Trend} from 'k6/metrics';

function positiveIntEnv(name, fallback) {
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

export function resolveMultiRunConfig() {
    return {
        baseUrl: __ENV.BASE_URL || 'http://localhost:8080',
        vus: positiveIntEnv('VUS', 1000),
        rampUpSeconds: nonNegativeIntEnv('RAMP_UP_SECONDS', 5),
        maxDuration: __ENV.MAX_DURATION || '5m',
        p95Ms: positiveIntEnv('P95_MS', 1000),
        failOnAssertion: boolEnv('FAIL_ON_ASSERTION', false),
        courseCount: positiveIntEnv('MULTI_COURSE_COUNT', 50),
        competitionMultiplier: positiveIntEnv('MULTI_COMPETITION_MULTIPLIER', 3),
        maxTotalAttempts: positiveIntEnv('MULTI_MAX_TOTAL_ATTEMPTS', 10000),
        interleaveSeed: nonNegativeIntEnv('MULTI_INTERLEAVE_SEED', 20260222),
        requireEmptyEnrolled: boolEnv('MULTI_REQUIRE_EMPTY_ENROLLED', true),
    };
}

export function createMultiOptions(runConfig) {
    const thresholds = {
        enroll_latency_ms: [`p(95)<${runConfig.p95Ms}`],
        enroll_allowed_code_rate: ['rate==1'],
        enroll_status_unexpected: ['count==0'],
    };

    if (runConfig.failOnAssertion) {
        thresholds.domain_courses_within_capacity = ['rate==1'];
    }

    return {
        summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
        scenarios: {
            multi_course_competition: {
                executor: 'shared-iterations',
                vus: runConfig.vus,
                iterations: runConfig.maxTotalAttempts,
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
export const statusUnexpectedByCode = new Counter('enroll_status_unexpected_by_code');
export const statusUnexpected400 = new Counter('enroll_status_unexpected_400');
export const statusUnexpected404 = new Counter('enroll_status_unexpected_404');
export const statusUnexpected500 = new Counter('enroll_status_unexpected_500');
export const statusUnexpectedOther = new Counter('enroll_status_unexpected_other');
export const enrollAttempts = new Counter('enroll_attempts');
export const allowedCodeRate = new Rate('enroll_allowed_code_rate');
export const enrollLatency = new Trend('enroll_latency_ms', true);
export const multiCourseCount = new Gauge('multi_course_count');
export const multiTotalCapacity = new Gauge('multi_total_capacity');
export const multiPlannedAttempts = new Gauge('multi_planned_attempts');
export const multiMaxConfiguredAttempts = new Gauge('multi_max_configured_attempts');
export const multiTotalEnrolledFinal = new Gauge('multi_total_enrolled_final');
export const domainCoursesWithinCapacity = new Rate('domain_courses_within_capacity');
