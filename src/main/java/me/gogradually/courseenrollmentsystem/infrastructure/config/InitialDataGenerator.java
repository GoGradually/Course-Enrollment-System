package me.gogradually.courseenrollmentsystem.infrastructure.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.TimeSlot;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.CourseJpaRepository;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.DepartmentJpaRepository;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.ProfessorJpaRepository;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.StudentJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Year;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InitialDataGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialDataGenerator.class);

    private static final List<String> BASE_DEPARTMENT_NAMES = List.of(
            "컴퓨터공학과",
            "전자공학과",
            "기계공학과",
            "화학공학과",
            "산업공학과",
            "수학과",
            "물리학과",
            "경영학과",
            "경제학과",
            "심리학과",
            "생명과학과",
            "국어국문학과"
    );

    private static final List<String> BASE_DEPARTMENT_CODES = List.of(
            "CSE",
            "EEE",
            "MEE",
            "CHE",
            "INE",
            "MAT",
            "PHY",
            "BUS",
            "ECO",
            "PSY",
            "BIO",
            "KOR"
    );

    private static final List<String> FAMILY_NAMES = List.of(
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오", "서", "신", "권", "황", "안", "송", "류", "전"
    );

    private static final List<String> GIVEN_NAME_FIRST = List.of(
            "민", "서", "도", "하", "지", "수", "현", "윤", "주", "태", "예", "다", "가", "준", "유", "채", "진", "시", "우", "나"
    );

    private static final List<String> GIVEN_NAME_SECOND = List.of(
            "준", "연", "원", "은", "빈", "아", "진", "서", "현", "율", "호", "린", "영", "혁", "정", "경", "희", "재", "우", "민"
    );

    private static final List<String> COURSE_TOPICS = List.of(
            "자료구조", "알고리즘", "운영체제", "컴퓨터네트워크", "데이터베이스", "소프트웨어공학", "인공지능", "머신러닝",
            "회로이론", "전자기학", "디지털신호처리", "마이크로프로세서", "열역학", "유체역학", "재료역학", "제어공학",
            "공정제어", "화공양론", "반응공학", "분리공정", "최적화이론", "확률통계", "시스템분석", "경영과학",
            "미분적분학", "선형대수", "확률론", "수치해석", "고전역학", "양자역학", "전자기파", "광학",
            "회계원리", "재무관리", "마케팅관리", "조직행동", "미시경제학", "거시경제학", "계량경제학", "국제경제학",
            "인지심리학", "발달심리학", "사회심리학", "상담심리", "분자생물학", "유전학", "세포생물학", "생태학",
            "국어학개론", "현대문학", "고전문학", "문예비평"
    );

    private static final List<String> COURSE_SUFFIXES = List.of("개론", "이론", "응용", "실습", "세미나", "프로젝트");

    private static final List<LocalTime> START_TIMES = List.of(
            LocalTime.of(9, 0),
            LocalTime.of(10, 30),
            LocalTime.of(13, 0),
            LocalTime.of(14, 30),
            LocalTime.of(16, 0)
    );

    private final InitialDataProperties properties;
    private final DepartmentJpaRepository departmentJpaRepository;
    private final ProfessorJpaRepository professorJpaRepository;
    private final StudentJpaRepository studentJpaRepository;
    private final CourseJpaRepository courseJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public InitialDataGenerator(
            InitialDataProperties properties,
            DepartmentJpaRepository departmentJpaRepository,
            ProfessorJpaRepository professorJpaRepository,
            StudentJpaRepository studentJpaRepository,
            CourseJpaRepository courseJpaRepository
    ) {
        this.properties = properties;
        this.departmentJpaRepository = departmentJpaRepository;
        this.professorJpaRepository = professorJpaRepository;
        this.studentJpaRepository = studentJpaRepository;
        this.courseJpaRepository = courseJpaRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        generateIfEmpty();
    }

    @Transactional
    public void generateIfEmpty() {
        if (alreadySeeded()) {
            log.info(
                    "Initial data generation skipped. departments={}, professors={}, students={}, courses={}",
                    departmentJpaRepository.count(),
                    professorJpaRepository.count(),
                    studentJpaRepository.count(),
                    courseJpaRepository.count()
            );
            return;
        }

        long startedAt = System.nanoTime();
        Random random = new Random(properties.randomSeed());

        DepartmentSeedState departmentSeedState = generateDepartments();
        List<ProfessorSeed> professorSeeds = generateProfessors(departmentSeedState.departmentIds(), random);
        generateStudents(departmentSeedState.departmentIds(), random);
        generateCourses(
                departmentSeedState.departmentIds(),
                departmentSeedState.departmentCodeById(),
                professorSeeds,
                random
        );

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        log.info(
                "Initial data generation completed. departments={}, professors={}, students={}, courses={}, elapsedMs={}",
                departmentJpaRepository.count(),
                professorJpaRepository.count(),
                studentJpaRepository.count(),
                courseJpaRepository.count(),
                elapsedMillis
        );
    }

    private boolean alreadySeeded() {
        return departmentJpaRepository.count() > 0
                || professorJpaRepository.count() > 0
                || studentJpaRepository.count() > 0
                || courseJpaRepository.count() > 0;
    }

    private DepartmentSeedState generateDepartments() {
        List<Long> departmentIds = new ArrayList<>(properties.departmentCount());
        Map<Long, String> departmentCodeById = new HashMap<>(properties.departmentCount());

        for (int i = 0; i < properties.departmentCount(); i++) {
            DepartmentTemplate template = resolveDepartmentTemplate(i);
            Department department = new Department(template.name());
            entityManager.persist(department);

            departmentIds.add(department.getId());
            departmentCodeById.put(department.getId(), template.code());
        }

        entityManager.flush();
        entityManager.clear();
        return new DepartmentSeedState(List.copyOf(departmentIds), Map.copyOf(departmentCodeById));
    }

    private List<ProfessorSeed> generateProfessors(List<Long> departmentIds, Random random) {
        List<ProfessorSeed> professorSeeds = new ArrayList<>(properties.professorCount());

        for (int i = 0; i < properties.professorCount(); i++) {
            Long departmentId = departmentIds.get(i % departmentIds.size());

            Professor professor = new Professor(
                    generateName(random, i),
                    entityManager.getReference(Department.class, departmentId)
            );

            entityManager.persist(professor);
            professorSeeds.add(new ProfessorSeed(professor.getId(), departmentId));
            flushAndClearIfNeeded(i);
        }

        entityManager.flush();
        entityManager.clear();
        return List.copyOf(professorSeeds);
    }

    private void generateStudents(List<Long> departmentIds, Random random) {
        int admissionYear = Year.now().getValue();

        for (int i = 0; i < properties.studentCount(); i++) {
            int sequence = i + 1;
            Long departmentId = departmentIds.get(i % departmentIds.size());

            Student student = new Student(
                    String.format("%04d%06d", admissionYear, sequence),
                    generateName(random, i + properties.professorCount()),
                    entityManager.getReference(Department.class, departmentId)
            );

            entityManager.persist(student);
            flushAndClearIfNeeded(i);
        }

        entityManager.flush();
        entityManager.clear();
    }

    private void generateCourses(
            List<Long> departmentIds,
            Map<Long, String> departmentCodeById,
            List<ProfessorSeed> professorSeeds,
            Random random
    ) {
        Map<Long, List<Long>> professorIdsByDepartment = new HashMap<>();
        List<Long> allProfessorIds = new ArrayList<>(professorSeeds.size());

        for (ProfessorSeed professorSeed : professorSeeds) {
            professorIdsByDepartment
                    .computeIfAbsent(professorSeed.departmentId(), ignored -> new ArrayList<>())
                    .add(professorSeed.professorId());
            allProfessorIds.add(professorSeed.professorId());
        }

        Map<Long, Integer> courseSequenceByDepartment = new HashMap<>();

        for (int i = 0; i < properties.courseCount(); i++) {
            Long departmentId = departmentIds.get(i % departmentIds.size());
            List<Long> departmentProfessorIds = professorIdsByDepartment.get(departmentId);
            List<Long> candidateProfessorIds = (departmentProfessorIds == null || departmentProfessorIds.isEmpty())
                    ? allProfessorIds
                    : departmentProfessorIds;

            Long professorId = candidateProfessorIds.get(i % candidateProfessorIds.size());
            int sequence = courseSequenceByDepartment.merge(departmentId, 1, Integer::sum);

            Course course = new Course(
                    createCourseCode(departmentCodeById.get(departmentId), sequence),
                    createCourseName(i, random),
                    pickCredits(random),
                    pickCapacity(i, random),
                    0,
                    createTimeSlot(i),
                    entityManager.getReference(Department.class, departmentId),
                    entityManager.getReference(Professor.class, professorId)
            );

            entityManager.persist(course);
            flushAndClearIfNeeded(i);
        }

        entityManager.flush();
        entityManager.clear();
    }

    private void flushAndClearIfNeeded(int index) {
        if ((index + 1) % properties.batchSize() == 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }

    private DepartmentTemplate resolveDepartmentTemplate(int index) {
        if (index < BASE_DEPARTMENT_NAMES.size()) {
            return new DepartmentTemplate(
                    BASE_DEPARTMENT_NAMES.get(index),
                    BASE_DEPARTMENT_CODES.get(index)
            );
        }

        int suffix = (index / BASE_DEPARTMENT_NAMES.size()) + 1;
        return new DepartmentTemplate(
                BASE_DEPARTMENT_NAMES.get(index % BASE_DEPARTMENT_NAMES.size()) + " " + suffix,
                String.format("DEP%02d", index + 1)
        );
    }

    private String generateName(Random random, int seedOffset) {
        String familyName = FAMILY_NAMES.get((seedOffset + random.nextInt(FAMILY_NAMES.size())) % FAMILY_NAMES.size());
        String first = GIVEN_NAME_FIRST.get((seedOffset + random.nextInt(GIVEN_NAME_FIRST.size())) % GIVEN_NAME_FIRST.size());
        String second = GIVEN_NAME_SECOND.get((seedOffset + random.nextInt(GIVEN_NAME_SECOND.size())) % GIVEN_NAME_SECOND.size());
        return familyName + first + second;
    }

    private String createCourseCode(String departmentCode, int sequence) {
        return departmentCode + String.format("%03d", 100 + sequence);
    }

    private String createCourseName(int index, Random random) {
        String topic = COURSE_TOPICS.get((index + random.nextInt(COURSE_TOPICS.size())) % COURSE_TOPICS.size());
        String suffix = COURSE_SUFFIXES.get(index % COURSE_SUFFIXES.size());
        return topic + " " + suffix;
    }

    private int pickCredits(Random random) {
        int roll = random.nextInt(10);
        if (roll < 2) {
            return 2;
        }
        if (roll < 8) {
            return 3;
        }
        return 4;
    }

    private int pickCapacity(int index, Random random) {
        if (index == 0) {
            return properties.hotCourseCapacity();
        }
        return 20 + random.nextInt(41);
    }

    private TimeSlot createTimeSlot(int index) {
        DayOfWeek dayOfWeek = DayOfWeek.of((index % 5) + 1);
        LocalTime startTime = START_TIMES.get((index / 5) % START_TIMES.size());
        return new TimeSlot(dayOfWeek, startTime, startTime.plusMinutes(90));
    }

    private record DepartmentTemplate(String name, String code) {
    }

    private record DepartmentSeedState(List<Long> departmentIds, Map<Long, String> departmentCodeById) {
    }

    private record ProfessorSeed(Long professorId, Long departmentId) {
    }
}
