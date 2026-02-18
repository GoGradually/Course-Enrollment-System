import http from 'k6/http';
import { sleep } from 'k6';
import {
  allowedCodeRate,
  enrollAttempts,
  enrollLatency,
  hotCourseCapacity,
  hotCourseId,
  status201,
  status409,
  status422,
  statusUnexpected,
} from './config.js';

function pickStudentId(studentIds, loops) {
  const globalIndex = ((__VU - 1) * loops + __ITER) % studentIds.length;
  return studentIds[globalIndex];
}

function rampDelaySeconds(rampUpSeconds, vus) {
  if (rampUpSeconds <= 0 || vus <= 1) {
    return 0;
  }

  return ((__VU - 1) / (vus - 1)) * rampUpSeconds;
}

export function runEnrollmentIteration(testData, enrollPath, scenarioName, runConfig) {
  const delay = rampDelaySeconds(runConfig.rampUpSeconds, runConfig.vus);
  if (__ITER === 0 && delay > 0) {
    sleep(delay);
  }

  enrollAttempts.add(1, { scenario: scenarioName });
  hotCourseId.add(testData.courseId, { scenario: scenarioName });
  hotCourseCapacity.add(testData.courseCapacity, { scenario: scenarioName });

  const studentId = pickStudentId(testData.studentIds, runConfig.loops);
  const payload = JSON.stringify({
    studentId,
    courseId: testData.courseId,
  });

  const response = http.post(`${testData.baseUrl}${enrollPath}`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: {
      scenario: scenarioName,
      endpoint: enrollPath,
    },
    responseCallback: http.expectedStatuses(201, 409, 422),
  });

  enrollLatency.add(response.timings.duration, { scenario: scenarioName });

  const status = response.status;
  if (status === 201) {
    status201.add(1);
  } else if (status === 409) {
    status409.add(1);
  } else if (status === 422) {
    status422.add(1);
  } else {
    statusUnexpected.add(1);
  }

  allowedCodeRate.add(status === 201 || status === 409 || status === 422);
}
