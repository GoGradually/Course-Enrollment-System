import http from 'k6/http';
import {domainCoursesWithinCapacity, multiTotalEnrolledFinal} from './multi-config.js';

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

function fetchAllCourses(baseUrl) {
    let offset = 0;
    const courses = [];

    while (true) {
        const response = http.get(`${baseUrl}/courses?offset=${offset}&limit=${MAX_PAGE_SIZE}`);
        const page = parseJson(response, 'GET /courses (teardown)');

        if (page.length === 0) {
            break;
        }

        courses.push(...page);
        if (page.length < MAX_PAGE_SIZE) {
            break;
        }
        offset += MAX_PAGE_SIZE;
    }

    return courses;
}

export function assertMultiCoursesWithinCapacity(testData, scenarioName, runConfig) {
    const allCourses = fetchAllCourses(testData.baseUrl);
    const courseById = new Map(allCourses.map((course) => [course.id, course]));
    const violations = [];
    const missingCourses = [];
    let totalEnrolledFinal = 0;

    for (const expectedCourse of testData.courses) {
        const currentCourse = courseById.get(expectedCourse.id);
        if (!currentCourse) {
            missingCourses.push(expectedCourse.id);
            domainCoursesWithinCapacity.add(false, {scenario: scenarioName, course_id: String(expectedCourse.id)});
            continue;
        }

        const pass = currentCourse.enrolled <= currentCourse.capacity;
        domainCoursesWithinCapacity.add(pass, {scenario: scenarioName, course_id: String(expectedCourse.id)});
        totalEnrolledFinal += currentCourse.enrolled;

        if (!pass) {
            violations.push({
                courseId: currentCourse.id,
                enrolled: currentCourse.enrolled,
                capacity: currentCourse.capacity,
            });
        }
    }

    multiTotalEnrolledFinal.add(totalEnrolledFinal, {scenario: scenarioName});

    const passAll = violations.length === 0 && missingCourses.length === 0;
    if (!passAll && runConfig.failOnAssertion) {
        const violationPreview = violations
            .slice(0, 5)
            .map((item) => `courseId=${item.courseId}, enrolled=${item.enrolled}, capacity=${item.capacity}`)
            .join('; ');
        const missingPreview = missingCourses.slice(0, 5).join(', ');

        throw new Error(
            `Domain assertion failed in multi-course teardown. scenario=${scenarioName}, violations=${violations.length}, missingCourses=${missingCourses.length}, violationPreview=[${violationPreview}], missingPreview=[${missingPreview}]`,
        );
    }
}
