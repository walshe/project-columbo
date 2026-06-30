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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ConfluenceSummaryControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void shouldReturnJsonByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/summary/confluence"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void shouldReturnMarkdownWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/summary/confluence")
                        .param("format", "MARKDOWN"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("text/markdown")))
                .andExpect(content().string(containsString("# SuperTrend Confluence Report")));
    }

    @Test
    void shouldRejectUnknownFormat() throws Exception {
        mockMvc.perform(get("/api/v1/summary/confluence")
                        .param("format", "html"))
                .andExpect(status().isBadRequest());
    }
}
