package me.gogradually.courseenrollmentsystem.interfaces.web;

import me.gogradually.courseenrollmentsystem.application.student.StudentQueryService;
import me.gogradually.courseenrollmentsystem.application.student.StudentSummary;
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

@WebMvcTest(StudentController.class)
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentQueryService studentQueryService;

    @Test
    void shouldReturnStudents() throws Exception {
        given(studentQueryService.getStudents(null, null)).willReturn(
                List.of(new StudentSummary(1L, "20260001", "홍길동", 2L, "컴퓨터공학과"))
        );

        mockMvc.perform(get("/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].studentNumber").value("20260001"))
                .andExpect(jsonPath("$[0].departmentName").value("컴퓨터공학과"));
    }
}
