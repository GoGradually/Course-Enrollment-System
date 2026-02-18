import http from 'k6/http';
import { domainCapacityNotExceeded, hotCourseEnrolledFinal } from './config.js';

const MAX_PAGE_SIZE = 100;

function parseJson(response, description) {
  if (response.status !== 200) {
    throw new Error(`${description} failed. status=${response.status}`);
  }

  try {
    return JSON.parse(response.body);
  } catch (error) {
    throw new Error(`${description} returned invalid JSON`);
  }
}

function isFailOnAssertionEnabled() {
  const raw = __ENV.FAIL_ON_ASSERTION;
  if (!raw) {
    return false;
  }

  const normalized = raw.toLowerCase();
  if (normalized === '1' || normalized === 'true') {
    return true;
  }
  if (normalized === '0' || normalized === 'false') {
    return false;
  }

  throw new Error('FAIL_ON_ASSERTION must be true/false or 1/0');
}

function fetchCourseById(baseUrl, courseId) {
  let offset = 0;

  while (true) {
    const response = http.get(`${baseUrl}/courses?offset=${offset}&limit=${MAX_PAGE_SIZE}`);
    const page = parseJson(response, 'GET /courses (teardown)');

    if (page.length === 0) {
      break;
    }

    const matched = page.find((course) => course.id === courseId);
    if (matched) {
      return matched;
    }

    if (page.length < MAX_PAGE_SIZE) {
      break;
    }
    offset += MAX_PAGE_SIZE;
  }

  throw new Error(`Course not found in teardown. courseId=${courseId}`);
}

export function assertCapacityNotExceeded(testData, scenarioName) {
  const course = fetchCourseById(testData.baseUrl, testData.courseId);
  const pass = course.enrolled <= course.capacity;

  hotCourseEnrolledFinal.add(course.enrolled, { scenario: scenarioName });
  domainCapacityNotExceeded.add(pass, { scenario: scenarioName });

  if (!pass && isFailOnAssertionEnabled()) {
    throw new Error(
      `Domain assertion failed: enrolled exceeds capacity. scenario=${scenarioName}, courseId=${course.id}, enrolled=${course.enrolled}, capacity=${course.capacity}`,
    );
  }
}
