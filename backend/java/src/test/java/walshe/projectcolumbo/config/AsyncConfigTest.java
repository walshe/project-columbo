package walshe.projectcolumbo.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class AsyncConfigTest {

    @Autowired(required = false)
    private ThreadPoolTaskExecutor indicatorComputationExecutor;

    @Test
    public void testExecutorBeanExists() {
        assertNotNull(indicatorComputationExecutor, "indicatorComputationExecutor bean should be created");
    }

    @Test
    public void testExecutorPoolSizeConfiguration() {
        assertNotNull(indicatorComputationExecutor);
        int corePoolSize = indicatorComputationExecutor.getCorePoolSize();
        int maxPoolSize = indicatorComputationExecutor.getMaxPoolSize();
        int queueCapacity = indicatorComputationExecutor.getQueueCapacity();

        assertEquals(8, corePoolSize, "Core pool size should be configured");
        assertEquals(16, maxPoolSize, "Max pool size should be configured");
        assertEquals(100, queueCapacity, "Queue capacity should be configured");
    }

    @Test
    public void testExecutorIsInitialized() {
        assertNotNull(indicatorComputationExecutor);
        assertNotNull(indicatorComputationExecutor.getThreadPoolExecutor(), "Thread pool executor should be initialized");
    }

    @Test
    public void testExecutorThreadNamePrefix() {
        assertNotNull(indicatorComputationExecutor);
        String threadNamePrefix = indicatorComputationExecutor.getThreadNamePrefix();
        assertEquals("indicator-", threadNamePrefix, "Thread name prefix should be set correctly");
    }
}
