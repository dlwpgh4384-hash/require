package com.company.common;

import com.company.common.schema.SchemaPreparation;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.Arrays;

@SpringBootApplication(scanBasePackages = {"com.company.common", "navigation"})
public class NavigationApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(NavigationApplication.class);
        if (Arrays.asList(args).contains("--prepare-schema")) {
            application.setWebApplicationType(WebApplicationType.NONE);
            try (var context = application.run(args)) {
                context.getBean(SchemaPreparation.class).prepare();
            } catch (Exception exception) {
                System.err.println("Schema preparation refused: " + SchemaPreparation.diagnostic(exception));
                System.exit(1);
            }
        } else {
            application.run(args);
        }
    }
}
