package walshe.projectcolumbo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProjectColumboApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectColumboApplication.class, args);
    }

}
