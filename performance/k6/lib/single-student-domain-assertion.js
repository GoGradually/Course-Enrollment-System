import http from 'k6/http';
import {
    singleStudentCreditsWithinLimit,
    singleStudentEnrolledCoursesFinal,
    singleStudentTimetableConflictFree,
    singleStudentTotalCreditsFinal,
} from './single-student-config.js';

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

function parseSchedule(schedule) {
    const matched = /^([A-Z]{3})\s+(\d{2}):(\d{2})-(\d{2}):(\d{2})$/.exec(schedule.trim());
    if (!matched) {
        return null;
    }

    const [, day, startHour, startMinute, endHour, endMinute] = matched;
    const start = (Number.parseInt(startHour, 10) * 60) + Number.parseInt(startMinute, 10);
    const end = (Number.parseInt(endHour, 10) * 60) + Number.parseInt(endMinute, 10);
    if (Number.isNaN(start) || Number.isNaN(end) || start >= end) {
        return null;
    }

    return {day, start, end};
}

function hasTimetableConflict(courses) {
    const slotsByDay = new Map();

    for (const course of courses) {
        const parsed = parseSchedule(`${course.schedule || ''}`);
        if (!parsed) {
            continue;
        }
        if (!slotsByDay.has(parsed.day)) {
            slotsByDay.set(parsed.day, []);
        }
        slotsByDay.get(parsed.day).push(parsed);
    }

    for (const daySlots of slotsByDay.values()) {
        daySlots.sort((left, right) => left.start - right.start);
        for (let index = 1; index < daySlots.length; index += 1) {
            if (daySlots[index].start < daySlots[index - 1].end) {
                return true;
            }
        }
    }

    return false;
}

export function assertSingleStudentTimetableInvariants(testData, scenarioName, runConfig) {
    const response = http.get(`${testData.baseUrl}/students/${testData.studentId}/timetable`);
    const timetable = parseJson(response, `GET /students/${testData.studentId}/timetable (teardown)`);

    const totalCredits = Number.parseInt(`${timetable.totalCredits}`, 10);
    const enrolledCourses = Array.isArray(timetable.courses) ? timetable.courses : [];
    const creditsWithinLimit = !Number.isNaN(totalCredits) && totalCredits <= 18;
    const conflictFree = !hasTimetableConflict(enrolledCourses);

    singleStudentTotalCreditsFinal.add(totalCredits, {scenario: scenarioName});
    singleStudentEnrolledCoursesFinal.add(enrolledCourses.length, {scenario: scenarioName});
    singleStudentCreditsWithinLimit.add(creditsWithinLimit, {scenario: scenarioName});
    singleStudentTimetableConflictFree.add(conflictFree, {scenario: scenarioName});

    if (runConfig.failOnAssertion && (!creditsWithinLimit || !conflictFree)) {
        throw new Error(
            `Single-student domain assertion failed. scenario=${scenarioName}, studentId=${testData.studentId}, totalCredits=${totalCredits}, conflictFree=${conflictFree}`,
        );
    }
}
