package walshe.projectcolumbo.api.v1.summary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import walshe.projectcolumbo.TestcontainersConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SummaryControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void shouldReturnJsonResponseByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/summary")
                        .param("timeframe", "D1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void shouldReturnMarkdownWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/summary")
                        .param("timeframe", "D1")
                        .param("format", "markdown"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("text/markdown")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# Market Summary Report")));
    }

    @Test
    void shouldReturnHtmlWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/summary")
                        .param("timeframe", "D1")
                        .param("format", "html"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<h1>Market Summary Report</h1>")));
    }
}
