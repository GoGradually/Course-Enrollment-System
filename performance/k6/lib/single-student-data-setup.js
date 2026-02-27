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

function fetchAllStudents(baseUrl) {
    let offset = 0;
    const students = [];

    while (true) {
        const response = http.get(`${baseUrl}/students?offset=${offset}&limit=${MAX_PAGE_SIZE}`);
        const page = parseJson(response, 'GET /students');

        if (page.length === 0) {
            break;
        }

        students.push(...page);
        if (page.length < MAX_PAGE_SIZE) {
            break;
        }
        offset += MAX_PAGE_SIZE;
    }

    if (students.length === 0) {
        throw new Error('No students found for test setup');
    }

    return students;
}

function fetchTimetable(baseUrl, studentId) {
    const response = http.get(`${baseUrl}/students/${studentId}/timetable`);
    return parseJson(response, `GET /students/${studentId}/timetable`);
}

function isTimetableEmpty(timetable) {
    const courseCount = Array.isArray(timetable.courses) ? timetable.courses.length : 0;
    return timetable.totalCredits === 0 && courseCount === 0;
}

function resolveSingleStudentId(runConfig) {
    if (runConfig.studentId !== null) {
        const timetable = fetchTimetable(runConfig.baseUrl, runConfig.studentId);
        if (runConfig.requireEmptyTimetable && !isTimetableEmpty(timetable)) {
            throw new Error(
                `Selected SINGLE_STUDENT_ID has existing timetable. studentId=${runConfig.studentId}, totalCredits=${timetable.totalCredits}`,
            );
        }
        return runConfig.studentId;
    }

    const students = fetchAllStudents(runConfig.baseUrl);
    if (!runConfig.requireEmptyTimetable) {
        return students[0].id;
    }

    for (const student of students) {
        const timetable = fetchTimetable(runConfig.baseUrl, student.id);
        if (isTimetableEmpty(timetable)) {
            return student.id;
        }
    }

    throw new Error('No student with empty timetable found');
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

function normalizeCourses(courses) {
    return courses
        .map((course) => ({
            id: Number.parseInt(`${course.id}`, 10),
            capacity: Number.parseInt(`${course.capacity}`, 10),
            enrolled: Number.parseInt(`${course.enrolled}`, 10),
            credits: Number.parseInt(`${course.credits}`, 10),
            schedule: `${course.schedule || ''}`.trim(),
        }))
        .filter((course) =>
            !Number.isNaN(course.id)
            && !Number.isNaN(course.capacity)
            && !Number.isNaN(course.enrolled)
            && !Number.isNaN(course.credits)
            && course.capacity > 0
            && course.schedule.length > 0,
        )
        .sort((left, right) => left.id - right.id);
}

function selectSpreadCourses(courses, courseCount) {
    const selected = [];
    const seenSchedules = new Set();

    for (const course of courses) {
        if (course.enrolled !== 0) {
            continue;
        }
        if (seenSchedules.has(course.schedule)) {
            continue;
        }
        selected.push(course);
        seenSchedules.add(course.schedule);
        if (selected.length >= courseCount) {
            return selected;
        }
    }

    throw new Error(
        `Not enough courses with distinct schedules. required=${courseCount}, actual=${selected.length}`,
    );
}

function selectConflictCourses(courses, courseCount) {
    const courseGroups = new Map();
    for (const course of courses) {
        if (course.enrolled !== 0) {
            continue;
        }
        if (!courseGroups.has(course.schedule)) {
            courseGroups.set(course.schedule, []);
        }
        courseGroups.get(course.schedule).push(course);
    }

    const groups = Array.from(courseGroups.values())
        .filter((group) => group.length >= courseCount)
        .sort((left, right) => {
            if (right.length !== left.length) {
                return right.length - left.length;
            }
            return left[0].id - right[0].id;
        });

    if (groups.length === 0) {
        throw new Error(
            `No same-schedule course group large enough for conflict test. required=${courseCount}`,
        );
    }

    return groups[0].slice(0, courseCount);
}

export function setupSingleStudentData(runConfig, scenarioSpec) {
    const studentId = resolveSingleStudentId(runConfig);
    const courses = normalizeCourses(fetchAllCourses(runConfig.baseUrl));

    let selectedCourses;
    if (scenarioSpec.selectionMode === 'conflict') {
        selectedCourses = selectConflictCourses(courses, runConfig.courseCount);
    } else if (scenarioSpec.selectionMode === 'spread') {
        selectedCourses = selectSpreadCourses(courses, runConfig.courseCount);
    } else {
        throw new Error(`Unsupported selection mode: ${scenarioSpec.selectionMode}`);
    }

    const requests = selectedCourses.map((course) => ({
        studentId,
        courseId: course.id,
    }));
    shuffleInPlace(requests, runConfig.interleaveSeed);

    return {
        baseUrl: runConfig.baseUrl,
        studentId,
        requests,
        courses: selectedCourses.map((course) => ({
            id: course.id,
            capacity: course.capacity,
            credits: course.credits,
            schedule: course.schedule,
        })),
        plannedAttempts: requests.length,
        selectionMode: scenarioSpec.selectionMode,
    };
}
