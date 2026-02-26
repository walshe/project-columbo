package walshe.projectcolumbo.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.ingestion.IngestionAlreadyRunningException;
import walshe.projectcolumbo.ingestion.MarketPipelineService;
import walshe.projectcolumbo.ingestion.IngestionRun;
import walshe.projectcolumbo.ingestion.IngestionRunStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IngestionControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private MarketPipelineService pipelineService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void shouldTriggerIngestion() throws Exception {
        // Given
        IngestionRun run = new IngestionRun();
        java.lang.reflect.Field field = IngestionRun.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(run, 123L);
        run.setStatus(IngestionRunStatus.RUNNING);
        
        when(pipelineService.runDaily(any(), any(), any())).thenReturn(run);

        // When / Then
        mockMvc.perform(post("/api/v1/internal/ingestion/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\": \"BINANCE\", \"timeframe\": \"1D\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(123))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void shouldReturn409IfAlreadyRunning() throws Exception {
        // Given
        when(pipelineService.runDaily(any(), any(), any()))
                .thenThrow(new IngestionAlreadyRunningException("Already running"));

        // When / Then
        mockMvc.perform(post("/api/v1/internal/ingestion/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Ingestion Already Running"))
                .andExpect(jsonPath("$.error_code").value("INGESTION_ALREADY_RUNNING"));
    }
}
