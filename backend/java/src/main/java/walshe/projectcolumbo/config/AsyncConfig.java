package walshe.projectcolumbo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "indicatorComputationExecutor")
    public Executor indicatorComputationExecutor(AsyncProperties asyncProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int corePoolSize = asyncProperties.getIndicatorComputation().getCorePoolSize();
        int maxPoolSize = asyncProperties.getIndicatorComputation().getMaxPoolSize();
        int queueCapacity = asyncProperties.getIndicatorComputation().getQueueCapacity();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("indicator-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }

    @Configuration
    @ConfigurationProperties(prefix = "app.async")
    public static class AsyncProperties {
        private ExecutorProperties indicatorComputation = new ExecutorProperties();

        public ExecutorProperties getIndicatorComputation() {
            return indicatorComputation;
        }

        public void setIndicatorComputation(ExecutorProperties indicatorComputation) {
            this.indicatorComputation = indicatorComputation;
        }

        public static class ExecutorProperties {
            private int corePoolSize = 8;
            private int maxPoolSize = 16;
            private int queueCapacity = 100;

            public int getCorePoolSize() {
                return corePoolSize;
            }

            public void setCorePoolSize(int corePoolSize) {
                this.corePoolSize = corePoolSize;
            }

            public int getMaxPoolSize() {
                return maxPoolSize;
            }

            public void setMaxPoolSize(int maxPoolSize) {
                this.maxPoolSize = maxPoolSize;
            }

            public int getQueueCapacity() {
                return queueCapacity;
            }

            public void setQueueCapacity(int queueCapacity) {
                this.queueCapacity = queueCapacity;
            }
        }
    }
}
