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

function optionalPositiveIntEnv(name) {
    const raw = __ENV[name];
    if (!raw) {
        return null;
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

export function resolveSingleStudentRunConfig() {
    const vus = positiveIntEnv('VUS', 1000);
    const courseCount = positiveIntEnv('SINGLE_STUDENT_COURSE_COUNT', 12);
    const effectiveVus = Math.min(vus, courseCount);

    return {
        baseUrl: __ENV.BASE_URL || 'http://localhost:8080',
        vus,
        effectiveVus,
        rampUpSeconds: nonNegativeIntEnv('RAMP_UP_SECONDS', 5),
        maxDuration: __ENV.MAX_DURATION || '2m',
        p95Ms: positiveIntEnv('P95_MS', 1000),
        failOnAssertion: boolEnv('FAIL_ON_ASSERTION', false),
        studentId: optionalPositiveIntEnv('SINGLE_STUDENT_ID'),
        courseCount,
        interleaveSeed: nonNegativeIntEnv('SINGLE_STUDENT_INTERLEAVE_SEED', 20260227),
        requireEmptyTimetable: boolEnv('SINGLE_STUDENT_REQUIRE_EMPTY_TIMETABLE', true),
    };
}

export function createSingleStudentOptions(runConfig, scenarioSpec) {
    const thresholds = {
        enroll_latency_ms: [`p(95)<${runConfig.p95Ms}`],
        enroll_allowed_code_rate: ['rate==1'],
        enroll_status_unexpected: ['count==0'],
        enroll_attempts: [`count==${runConfig.courseCount}`],
    };

    if (runConfig.failOnAssertion) {
        thresholds.single_student_credits_within_limit = ['rate==1'];
        thresholds.single_student_timetable_conflict_free = ['rate==1'];
        if (scenarioSpec.expectScheduleConflict) {
            thresholds.single_student_422_schedule_conflict = ['count>0'];
        }
    }

    return {
        summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
        scenarios: {
            single_student_multi_course: {
                executor: 'shared-iterations',
                vus: runConfig.effectiveVus,
                iterations: runConfig.courseCount,
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

export const singleStudentId = new Gauge('single_student_id');
export const singleStudentPlannedAttempts = new Gauge('single_student_planned_attempts');
export const singleStudentTotalCreditsFinal = new Gauge('single_student_total_credits_final');
export const singleStudentEnrolledCoursesFinal = new Gauge('single_student_enrolled_courses_final');
export const singleStudentCreditsWithinLimit = new Rate('single_student_credits_within_limit');
export const singleStudentTimetableConflictFree = new Rate('single_student_timetable_conflict_free');

export const singleStudent409Duplicate = new Counter('single_student_409_duplicate');
export const singleStudent409ConcurrencyConflict = new Counter('single_student_409_concurrency_conflict');
export const singleStudent409Other = new Counter('single_student_409_other');

export const singleStudent422ScheduleConflict = new Counter('single_student_422_schedule_conflict');
export const singleStudent422CreditLimit = new Counter('single_student_422_credit_limit');
export const singleStudent422Capacity = new Counter('single_student_422_capacity');
export const singleStudent422Other = new Counter('single_student_422_other');
