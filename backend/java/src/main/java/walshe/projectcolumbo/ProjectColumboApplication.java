package walshe.projectcolumbo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import walshe.projectcolumbo.ingestion.IngestionProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(IngestionProperties.class)
public class ProjectColumboApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectColumboApplication.class, args);
    }

}
