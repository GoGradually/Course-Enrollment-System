import {createMultiOptions, resolveMultiRunConfig} from '../lib/multi-config.js';
import {assertMultiCoursesWithinCapacity} from '../lib/multi-domain-assertion.js';
import {setupMultiData} from '../lib/multi-data-setup.js';
import {runMultiEnrollmentIteration} from '../lib/multi-enroll-runner.js';
import {createMultiSummary} from '../lib/multi-summary.js';

const SCENARIO_NAME = 'rc-atomic-multi';
// atomic = SQL direct strategy (/enrollments/atomic)
const ENROLL_PATH = '/enrollments/atomic';
const RUN_CONFIG = resolveMultiRunConfig();

export const options = createMultiOptions(RUN_CONFIG);

export function setup() {
    return setupMultiData(RUN_CONFIG);
}

export default function (testData) {
    runMultiEnrollmentIteration(testData, ENROLL_PATH, SCENARIO_NAME, RUN_CONFIG);
}

export function teardown(testData) {
    assertMultiCoursesWithinCapacity(testData, SCENARIO_NAME, RUN_CONFIG);
}

export function handleSummary(data) {
    return createMultiSummary(data, SCENARIO_NAME, RUN_CONFIG);
}
