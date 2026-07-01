package walshe.projectcolumbo.api.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import walshe.projectcolumbo.TestDatabaseCleaner;
import walshe.projectcolumbo.TestcontainersConfiguration;
import walshe.projectcolumbo.config.TimeProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SignalControllerFreshnessTest {

    // Fixed clock well past any period boundary + grace, so an empty DB is unambiguously
    // stale-beyond-grace. Also keeps BackfillStartValidator happy at startup.
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        TimeProvider fixedClock() {
            return () -> OffsetDateTime.of(2026, 7, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TestDatabaseCleaner testDatabaseCleaner;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        testDatabaseCleaner.cleanAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void flagsStaleWhenDataIsBehind() throws Exception {
        mockMvc.perform(get("/api/v1/signals")
                        .param("timeframe", "D1")
                        .param("indicatorType", "SUPERTREND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stale").value(true));
    }

    @Test
    void requireFreshReturns503WhenStaleBeyondGrace() throws Exception {
        mockMvc.perform(get("/api/v1/signals")
                        .param("timeframe", "D1")
                        .param("indicatorType", "SUPERTREND")
                        .param("requireFresh", "true"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"));
    }
}
