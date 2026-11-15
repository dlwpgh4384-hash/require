package com.company.common.support.controller;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThat;

/** Complements endpoint MockMvc tests without starting mapper-dependent application beans. */
class OpenApiControllerMappingContractTest {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options");

    @Test
    void everyDurableOpenApiOperationHasOneLiteralControllerHandler() throws Exception {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("contracts/openapi.yaml"));
        Map<String, Object> document = yaml.getObject();
        assertThat(document).as("durable classpath contract").isNotNull();
        assertThat(document.get("paths")).isInstanceOf(Map.class);
        Map<?, ?> paths = (Map<?, ?>) document.get("paths");
        assertThat(paths).isNotEmpty();
        Map<String, String> handlers = handlers();
        int operations = 0;
        for (var path : paths.entrySet()) {
            assertThat(path.getValue()).isInstanceOf(Map.class);
            for (Object method : ((Map<?, ?>) path.getValue()).keySet()) {
                String verb = method.toString();
                if (!HTTP_METHODS.contains(verb)) continue;
                operations++;
                String operation = key(verb, path.getKey().toString());
                assertThat(handlers).as("controller for %s", operation).containsKey(operation);
            }
        }
        assertThat(operations).as("contract contains HTTP operations").isPositive();
    }

    private Map<String, String> handlers() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Map<String, String> handlers = new LinkedHashMap<>();
        var candidates = scanner.findCandidateComponents("com.company.common");
        assertThat(candidates).as("real production controllers").isNotEmpty();
        for (var candidate : candidates) {
            Class<?> controller = ClassUtils.forName(candidate.getBeanClassName(), getClass().getClassLoader());
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String prefix = base == null ? "" : literalPath(base, controller.getName());
            for (var method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                String owner = controller.getName() + "#" + method.getName();
                String path = prefix + literalPath(mapping, owner);
                assertThat(mapping.method()).as("explicit HTTP method for %s", owner).isNotEmpty();
                for (RequestMethod verb : mapping.method()) {
                    String operation = key(verb.name(), path);
                    assertThat(handlers.putIfAbsent(operation, owner))
                        .as("unique handler for %s (%s)", operation, owner).isNull();
                }
            }
        }
        return handlers;
    }

    private String literalPath(RequestMapping mapping, String owner) {
        assertThat(mapping.path()).as("one literal path per handler: %s", owner).hasSizeLessThanOrEqualTo(1);
        String path = mapping.path().length == 0 ? "" : mapping.path()[0];
        assertThat(path).as("no runtime path interpolation: %s", owner).doesNotContain("${", "#{");
        return path;
    }

    private static String key(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + path.replaceAll("\\{[^}]+}", "{}");
    }
}
