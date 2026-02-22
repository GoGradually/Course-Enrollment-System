import http from 'k6/http';

const MAX_PAGE_SIZE = 100;
const DEFAULT_TARGET_CAPACITY = 100;

function parseJson(response, description) {
  const status = response.status;
  if (status !== 200) {
    throw new Error(`${description} failed. status=${status}`);
  }

  try {
    return JSON.parse(response.body);
  } catch (error) {
    throw new Error(`${description} returned invalid JSON`);
  }
}

function fetchCourses(baseUrl) {
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

function resolveHotCourse(courses) {
  const forcedCourseId = __ENV.HOT_COURSE_ID;
  if (forcedCourseId) {
    const parsedId = Number.parseInt(forcedCourseId, 10);
    if (Number.isNaN(parsedId)) {
      throw new Error('HOT_COURSE_ID must be an integer');
    }

    const matched = courses.find((course) => course.id === parsedId);
    if (matched) {
      return matched;
    }

    return { id: parsedId, capacity: -1 };
  }

  const targetCapacity = Number.parseInt(__ENV.TARGET_CAPACITY || `${DEFAULT_TARGET_CAPACITY}`, 10);
  if (Number.isNaN(targetCapacity) || targetCapacity <= 0) {
    throw new Error('TARGET_CAPACITY must be a positive integer');
  }

  const matched = courses.find((course) => course.capacity === targetCapacity && course.enrolled === 0);
  if (matched) {
    return matched;
  }

  throw new Error(`No available course found for target capacity. targetCapacity=${targetCapacity}`);
}

export function setupData(baseUrl, requiredStudents) {
  const courses = fetchCourses(baseUrl);
  const hotCourse = resolveHotCourse(courses);
  const studentIds = fetchStudentIds(baseUrl, requiredStudents);

  return {
    baseUrl,
    courseId: hotCourse.id,
    courseCapacity: hotCourse.capacity,
    studentIds,
  };
}
