import {createOptions, resolveRunConfig} from '../lib/config.js';
import {assertCapacityNotExceeded} from '../lib/domain-assertion.js';
import {setupData} from '../lib/data-setup.js';
import {runEnrollmentIteration} from '../lib/enroll-runner.js';
import {createSummary} from '../lib/summary.js';

const SCENARIO_NAME = 'rr-separated';
// separated = transaction-split strategy (/enrollments/separated)
const ENROLL_PATH = '/enrollments/separated';
const RUN_CONFIG = resolveRunConfig();

export const options = createOptions(RUN_CONFIG);

export function setup() {
  return setupData(RUN_CONFIG.baseUrl, RUN_CONFIG.vus * RUN_CONFIG.loops);
}

export default function (testData) {
  runEnrollmentIteration(testData, ENROLL_PATH, SCENARIO_NAME, RUN_CONFIG);
}

export function teardown(testData) {
  assertCapacityNotExceeded(testData, SCENARIO_NAME);
}

export function handleSummary(data) {
  return createSummary(data, SCENARIO_NAME, RUN_CONFIG);
}
