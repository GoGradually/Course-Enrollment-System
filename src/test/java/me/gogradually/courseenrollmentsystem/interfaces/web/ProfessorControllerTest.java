package me.gogradually.courseenrollmentsystem.interfaces.web;

import me.gogradually.courseenrollmentsystem.application.professor.ProfessorQueryService;
import me.gogradually.courseenrollmentsystem.application.professor.ProfessorSummary;
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

@WebMvcTest(ProfessorController.class)
class ProfessorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfessorQueryService professorQueryService;

    @Test
    void shouldReturnProfessors() throws Exception {
        given(professorQueryService.getProfessors(null, null)).willReturn(
                List.of(new ProfessorSummary(11L, "김교수", 2L, "컴퓨터공학과"))
        );

        mockMvc.perform(get("/professors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].name").value("김교수"))
                .andExpect(jsonPath("$[0].departmentName").value("컴퓨터공학과"));
    }
}
