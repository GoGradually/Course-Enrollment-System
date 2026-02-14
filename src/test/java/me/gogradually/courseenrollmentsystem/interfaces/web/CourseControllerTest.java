package me.gogradually.courseenrollmentsystem.interfaces.web;

import me.gogradually.courseenrollmentsystem.application.course.CourseQueryService;
import me.gogradually.courseenrollmentsystem.application.course.CourseSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseQueryService courseQueryService;

    @Test
    void shouldReturnCoursesWithRequiredFields() throws Exception {
        given(courseQueryService.getCourses(null, null, null)).willReturn(
                List.of(new CourseSummary(
                        101L,
                        "CSE301",
                        "자료구조",
                        3,
                        30,
                        25,
                        "MON 09:00-10:30",
                        2L,
                        "컴퓨터공학과",
                        11L,
                        "김교수"
                ))
        );

        mockMvc.perform(get("/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].name").value("자료구조"))
                .andExpect(jsonPath("$[0].credits").value(3))
                .andExpect(jsonPath("$[0].capacity").value(30))
                .andExpect(jsonPath("$[0].enrolled").value(25))
                .andExpect(jsonPath("$[0].schedule").value("MON 09:00-10:30"));
    }

    @Test
    void shouldApplyDepartmentFilter() throws Exception {
        given(courseQueryService.getCourses(2L, 10, 20)).willReturn(List.of());

        mockMvc.perform(
                        get("/courses")
                                .param("departmentId", "2")
                                .param("offset", "10")
                                .param("limit", "20")
                )
                .andExpect(status().isOk());
    }
}
