import http from 'k6/http';

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
        const page = parseJson(response, 'GET /courses');

        if (page.length === 0) {
            break;
        }

        courses.push(...page);
        if (page.length < MAX_PAGE_SIZE) {
            break;
        }
        offset += MAX_PAGE_SIZE;
    }

    if (courses.length === 0) {
        throw new Error('No courses found for test setup');
    }

    return courses;
}

function fetchStudentIds(baseUrl, requiredCount) {
    let offset = 0;
    const studentIds = [];

    while (studentIds.length < requiredCount) {
        const response = http.get(`${baseUrl}/students?offset=${offset}&limit=${MAX_PAGE_SIZE}`);
        const page = parseJson(response, 'GET /students');

        if (page.length === 0) {
            break;
        }

        for (const student of page) {
            studentIds.push(student.id);
            if (studentIds.length >= requiredCount) {
                break;
            }
        }

        if (page.length < MAX_PAGE_SIZE) {
            break;
        }
        offset += MAX_PAGE_SIZE;
    }

    if (studentIds.length < requiredCount) {
        throw new Error(`Not enough students. required=${requiredCount}, actual=${studentIds.length}`);
    }

    return studentIds;
}

function selectTargetCourses(courses, courseCount, requireEmptyEnrolled) {
    const normalized = courses
        .map((course) => ({
            id: Number.parseInt(`${course.id}`, 10),
            capacity: Number.parseInt(`${course.capacity}`, 10),
            enrolled: Number.parseInt(`${course.enrolled}`, 10),
        }))
        .filter((course) => !Number.isNaN(course.id) && !Number.isNaN(course.capacity) && !Number.isNaN(course.enrolled))
        .sort((left, right) => left.id - right.id);

    const candidateCourses = requireEmptyEnrolled
        ? normalized.filter((course) => course.enrolled === 0)
        : normalized;

    if (candidateCourses.length < courseCount) {
        throw new Error(
            `Not enough candidate courses. required=${courseCount}, actual=${candidateCourses.length}, requireEmptyEnrolled=${requireEmptyEnrolled}`,
        );
    }

    const selectedCourses = candidateCourses.slice(0, courseCount);
    const invalidCapacityCourse = selectedCourses.find((course) => course.capacity <= 0);
    if (invalidCapacityCourse) {
        throw new Error(`Selected course capacity must be positive. courseId=${invalidCapacityCourse.id}`);
    }

    return selectedCourses;
}

function createShuffleRng(seed) {
    let state = seed >>> 0;

    return function next() {
        state = (state + 0x6D2B79F5) >>> 0;
        let mixed = Math.imul(state ^ (state >>> 15), 1 | state);
        mixed ^= mixed + Math.imul(mixed ^ (mixed >>> 7), 61 | mixed);
        return ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296;
    };
}

function shuffleInPlace(items, seed) {
    const random = createShuffleRng(seed);
    for (let index = items.length - 1; index > 0; index -= 1) {
        const swapIndex = Math.floor(random() * (index + 1));
        [items[index], items[swapIndex]] = [items[swapIndex], items[index]];
    }
}

function buildRequestPlan(selectedCourses, studentIds, competitionMultiplier) {
    const requests = [];
    const coursePlans = [];
    let studentCursor = 0;
    let totalCapacity = 0;

    for (const course of selectedCourses) {
        const plannedAttempts = course.capacity * competitionMultiplier;
        totalCapacity += course.capacity;
        coursePlans.push({
            courseId: course.id,
            capacity: course.capacity,
            plannedAttempts,
        });

        for (let attempt = 0; attempt < plannedAttempts; attempt += 1) {
            requests.push({
                studentId: studentIds[studentCursor],
                courseId: course.id,
            });
            studentCursor += 1;
        }
    }

    return {
        requests,
        coursePlans,
        totalCapacity,
    };
}

export function setupMultiData(runConfig) {
    const courses = fetchAllCourses(runConfig.baseUrl);
    const selectedCourses = selectTargetCourses(courses, runConfig.courseCount, runConfig.requireEmptyEnrolled);
    const plannedAttempts = selectedCourses.reduce(
        (sum, course) => sum + (course.capacity * runConfig.competitionMultiplier),
        0,
    );

    if (plannedAttempts > runConfig.maxTotalAttempts) {
        throw new Error(
            `Planned attempts exceed MULTI_MAX_TOTAL_ATTEMPTS. planned=${plannedAttempts}, max=${runConfig.maxTotalAttempts}, reduce MULTI_COURSE_COUNT or MULTI_COMPETITION_MULTIPLIER`,
        );
    }

    const studentIds = fetchStudentIds(runConfig.baseUrl, plannedAttempts);
    const requestPlan = buildRequestPlan(selectedCourses, studentIds, runConfig.competitionMultiplier);
    shuffleInPlace(requestPlan.requests, runConfig.interleaveSeed);

    return {
        baseUrl: runConfig.baseUrl,
        requests: requestPlan.requests,
        courses: selectedCourses.map((course) => ({
            id: course.id,
            capacity: course.capacity,
        })),
        coursePlans: requestPlan.coursePlans,
        courseCount: selectedCourses.length,
        competitionMultiplier: runConfig.competitionMultiplier,
        totalCapacity: requestPlan.totalCapacity,
        plannedAttempts,
        maxConfiguredAttempts: runConfig.maxTotalAttempts,
    };
}
