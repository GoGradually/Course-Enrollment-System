import exec from 'k6/execution';
import http from 'k6/http';
import {sleep} from 'k6';
import {
    allowedCodeRate,
    enrollAttempts,
    enrollLatency,
    multiCourseCount,
    multiMaxConfiguredAttempts,
    multiPlannedAttempts,
    multiTotalCapacity,
    status201,
    status409,
    status422,
    statusUnexpected,
    statusUnexpected400,
    statusUnexpected404,
    statusUnexpected500,
    statusUnexpectedByCode,
    statusUnexpectedOther,
} from './multi-config.js';

function rampDelaySeconds(rampUpSeconds, vus) {
    if (rampUpSeconds <= 0 || vus <= 1) {
        return 0;
    }

    return ((__VU - 1) / (vus - 1)) * rampUpSeconds;
}

function recordWorkloadMetadata(testData, scenarioName) {
    multiCourseCount.add(testData.courseCount, {scenario: scenarioName});
    multiTotalCapacity.add(testData.totalCapacity, {scenario: scenarioName});
    multiPlannedAttempts.add(testData.plannedAttempts, {scenario: scenarioName});
    multiMaxConfiguredAttempts.add(testData.maxConfiguredAttempts, {scenario: scenarioName});
}

export function runMultiEnrollmentIteration(testData, enrollPath, scenarioName, runConfig) {
    const delay = rampDelaySeconds(runConfig.rampUpSeconds, runConfig.vus);
    if (exec.vu.iterationInScenario === 0 && delay > 0) {
        sleep(delay);
    }

    const iterationIndex = exec.scenario.iterationInTest;
    if (iterationIndex === 0) {
        recordWorkloadMetadata(testData, scenarioName);
    }

    const request = testData.requests[iterationIndex];
    if (!request) {
        return;
    }

    enrollAttempts.add(1, {scenario: scenarioName});

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

    allowedCodeRate.add(status === 201 || status === 409 || status === 422);
}
