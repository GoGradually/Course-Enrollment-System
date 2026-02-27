import {createSingleStudentOptions, resolveSingleStudentRunConfig} from '../lib/single-student-config.js';
import {setupSingleStudentData} from '../lib/single-student-data-setup.js';
import {runSingleStudentEnrollmentIteration} from '../lib/single-student-enroll-runner.js';
import {assertSingleStudentTimetableInvariants} from '../lib/single-student-domain-assertion.js';
import {createSingleStudentSummary} from '../lib/single-student-summary.js';

const SCENARIO_NAME = 'rc-separated-single-student-multi';
const ENROLL_PATH = '/enrollments/separated';
const SCENARIO_SPEC = {
    selectionMode: 'spread',
    expectScheduleConflict: false,
};
const RUN_CONFIG = resolveSingleStudentRunConfig();

export const options = createSingleStudentOptions(RUN_CONFIG, SCENARIO_SPEC);

export function setup() {
    return setupSingleStudentData(RUN_CONFIG, SCENARIO_SPEC);
}

export default function (testData) {
    runSingleStudentEnrollmentIteration(testData, ENROLL_PATH, SCENARIO_NAME, RUN_CONFIG);
}

export function teardown(testData) {
    assertSingleStudentTimetableInvariants(testData, SCENARIO_NAME, RUN_CONFIG);
}

export function handleSummary(data) {
    return createSingleStudentSummary(data, SCENARIO_NAME, RUN_CONFIG, SCENARIO_SPEC);
}
