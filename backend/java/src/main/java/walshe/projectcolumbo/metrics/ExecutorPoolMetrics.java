package walshe.projectcolumbo.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorPoolMetrics {

    public static class ExecutorMetricsCustomizer {
        private final MeterRegistry meterRegistry;

        public ExecutorMetricsCustomizer(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        public void registerExecutorMetrics(ThreadPoolTaskExecutor executor, String name) {
            ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
            if (threadPoolExecutor != null) {
                Tags tags = Tags.of("executor", name);

                meterRegistry.gaugeCollectionSize("executor.queue.size", tags, threadPoolExecutor.getQueue());

                // Register simple number gauges
                meterRegistry.gauge("executor.pool.active", tags, threadPoolExecutor, ThreadPoolExecutor::getActiveCount);
                meterRegistry.gauge("executor.pool.size", tags, threadPoolExecutor, ThreadPoolExecutor::getPoolSize);
                meterRegistry.gauge("executor.pool.max", tags, threadPoolExecutor, ThreadPoolExecutor::getMaximumPoolSize);
                meterRegistry.gauge("executor.tasks.completed", tags, threadPoolExecutor, ThreadPoolExecutor::getCompletedTaskCount);
            }
        }
    }
}
