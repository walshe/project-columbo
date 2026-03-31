package walshe.projectcolumbo;

import org.springframework.boot.SpringApplication;

public class TestProjectColumboApplication {

    public static void main(String[] args) {
        SpringApplication.from(ProjectColumboApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
