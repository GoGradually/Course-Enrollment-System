import exec from 'k6/execution';
import http from 'k6/http';
import {sleep} from 'k6';
import {
    allowedCodeRate,
    enrollAttempts,
    enrollLatency,
    singleStudent409ConcurrencyConflict,
    singleStudent409Duplicate,
    singleStudent409Other,
    singleStudent422Capacity,
    singleStudent422CreditLimit,
    singleStudent422Other,
    singleStudent422ScheduleConflict,
    singleStudentId,
    singleStudentPlannedAttempts,
    status201,
    status409,
    status422,
    statusUnexpected,
    statusUnexpected400,
    statusUnexpected404,
    statusUnexpected500,
    statusUnexpectedByCode,
    statusUnexpectedOther,
} from './single-student-config.js';

const ERROR_CODE_DUPLICATE = 'DUPLICATE_ENROLLMENT';
const ERROR_CODE_CONCURRENCY = 'ENROLLMENT_CONCURRENCY_CONFLICT';
const ERROR_CODE_SCHEDULE_CONFLICT = 'SCHEDULE_CONFLICT';
const ERROR_CODE_CREDIT_LIMIT = 'CREDIT_LIMIT_EXCEEDED';
const ERROR_CODE_CAPACITY = 'COURSE_CAPACITY_EXCEEDED';

function rampDelaySeconds(rampUpSeconds, vus) {
    if (rampUpSeconds <= 0 || vus <= 1) {
        return 0;
    }

    return ((__VU - 1) / (vus - 1)) * rampUpSeconds;
}

function parseErrorCode(response) {
    if (response.status !== 409 && response.status !== 422) {
        return null;
    }
    if (!response.body || response.body.length === 0) {
        return null;
    }

    try {
        const body = JSON.parse(response.body);
        return typeof body.code === 'string' ? body.code : null;
    } catch (error) {
        return null;
    }
}

function recordDomainErrorCode(status, errorCode) {
    if (status === 409) {
        if (errorCode === ERROR_CODE_DUPLICATE) {
            singleStudent409Duplicate.add(1);
        } else if (errorCode === ERROR_CODE_CONCURRENCY) {
            singleStudent409ConcurrencyConflict.add(1);
        } else {
            singleStudent409Other.add(1);
        }
        return;
    }

    if (status === 422) {
        if (errorCode === ERROR_CODE_SCHEDULE_CONFLICT) {
            singleStudent422ScheduleConflict.add(1);
        } else if (errorCode === ERROR_CODE_CREDIT_LIMIT) {
            singleStudent422CreditLimit.add(1);
        } else if (errorCode === ERROR_CODE_CAPACITY) {
            singleStudent422Capacity.add(1);
        } else {
            singleStudent422Other.add(1);
        }
    }
}

export function runSingleStudentEnrollmentIteration(testData, enrollPath, scenarioName, runConfig) {
    const delay = rampDelaySeconds(runConfig.rampUpSeconds, runConfig.effectiveVus);
    if (exec.vu.iterationInScenario === 0 && delay > 0) {
        sleep(delay);
    }

    const iterationIndex = exec.scenario.iterationInTest;
    const request = testData.requests[iterationIndex];
    if (!request) {
        return;
    }

    enrollAttempts.add(1, {scenario: scenarioName});
    singleStudentId.add(testData.studentId, {scenario: scenarioName});
    singleStudentPlannedAttempts.add(testData.plannedAttempts, {scenario: scenarioName});

    const payload = JSON.stringify({
        studentId: request.studentId,
        courseId: request.courseId,
    });

    const response = http.post(`${testData.baseUrl}${enrollPath}`, payload, {
        headers: {'Content-Type': 'application/json'},
        tags: {
            scenario: scenarioName,
            endpoint: enrollPath,
            course_id: String(request.courseId),
            student_id: String(request.studentId),
        },
        responseCallback: http.expectedStatuses(201, 409, 422),
    });

    enrollLatency.add(response.timings.duration, {scenario: scenarioName});

    const status = response.status;
    if (status === 201) {
        status201.add(1);
    } else if (status === 409) {
        status409.add(1);
    } else if (status === 422) {
        status422.add(1);
    } else {
        statusUnexpected.add(1);
        statusUnexpectedByCode.add(1, {status: String(status)});
        if (status === 400) {
            statusUnexpected400.add(1);
        } else if (status === 404) {
            statusUnexpected404.add(1);
        } else if (status === 500) {
            statusUnexpected500.add(1);
        } else {
            statusUnexpectedOther.add(1, {status: String(status)});
        }
    }

    recordDomainErrorCode(status, parseErrorCode(response));
    allowedCodeRate.add(status === 201 || status === 409 || status === 422);
}
