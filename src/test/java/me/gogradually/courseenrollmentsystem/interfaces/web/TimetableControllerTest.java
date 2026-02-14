package me.gogradually.courseenrollmentsystem.interfaces.web;

import me.gogradually.courseenrollmentsystem.application.timetable.StudentTimetable;
import me.gogradually.courseenrollmentsystem.application.timetable.TimetableCourseSummary;
import me.gogradually.courseenrollmentsystem.application.timetable.TimetableQueryService;
import me.gogradually.courseenrollmentsystem.domain.exception.StudentNotFoundException;
import me.gogradually.courseenrollmentsystem.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimetableController.class)
@Import(GlobalExceptionHandler.class)
class TimetableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TimetableQueryService timetableQueryService;

    @Test
    void shouldReturnStudentTimetable() throws Exception {
        StudentTimetable timetable = new StudentTimetable(
                1L,
                6,
                List.of(
                        new TimetableCourseSummary(
                                101L,
                                "자료구조",
                                3,
                                "MON 09:00-10:30",
                                "김교수",
                                "컴퓨터공학과"
                        ),
                        new TimetableCourseSummary(
                                201L,
                                "운영체제",
                                3,
                                "TUE 10:30-12:00",
                                "이교수",
                                "컴퓨터공학과"
                        )
                )
        );
        given(timetableQueryService.getStudentTimetable(1L)).willReturn(timetable);

        mockMvc.perform(get("/students/{studentId}/timetable", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(1))
                .andExpect(jsonPath("$.totalCredits").value(6))
                .andExpect(jsonPath("$.courses[0].courseId").value(101))
                .andExpect(jsonPath("$.courses[0].schedule").value("MON 09:00-10:30"));
    }

    @Test
    void shouldReturnNotFoundWhenStudentMissing() throws Exception {
        given(timetableQueryService.getStudentTimetable(999L)).willThrow(new StudentNotFoundException(999L));

        mockMvc.perform(get("/students/{studentId}/timetable", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
