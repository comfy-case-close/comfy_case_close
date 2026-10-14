package com.fnbx.hrm;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * The service's entity scan spans three packages (hrm's own plus identity's). Two classes with the same simple name
 * in them stop the application at start-up ("share the entity name"), so catch it here, without a database.
 * Typical cause: a class moved between libraries whose old compiled .class file is still in target/.
 */
class EntityMappingTest {

    @Test
    void everyScannedEntityBindsWithoutDuplicateNames() throws ClassNotFoundException {
        EntityScan scan = HrmApplication.class.getAnnotation(EntityScan.class);
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(jakarta.persistence.Entity.class));

        List<Class<?>> entities = new ArrayList<>();
        for (String basePackage : scan.basePackages()) {
            for (var candidate : provider.findCandidateComponents(basePackage)) {
                entities.add(Class.forName(candidate.getBeanClassName()));
            }
        }
        assertFalse(entities.isEmpty(), "the entity scan found nothing");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            entities.forEach(sources::addAnnotatedClass);
            sources.buildMetadata();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
