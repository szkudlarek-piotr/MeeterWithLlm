package org.example.meeter;

import jakarta.persistence.Entity;
import java.nio.file.*;
import java.util.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import static org.junit.jupiter.api.Assertions.*;

/** Regenerates a reference schema in target only; never connects to user data. */
class SchemaExportTests {
    @Test void exportsCurrentMariaDbMappingForReview() throws Exception {
        var destination=Path.of("target", "current-mariadb-schema.sql").toAbsolutePath();
        Files.deleteIfExists(destination);
        Map<String,Object> settings=new HashMap<>();
        settings.put("hibernate.dialect", new org.hibernate.dialect.MariaDBDialect(org.hibernate.dialect.DatabaseVersion.make(10, 7)));
        settings.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        settings.put("hibernate.boot.allow_jdbc_metadata_access", false);
        settings.put("jakarta.persistence.schema-generation.database.action", "none");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", destination.toString());
        settings.put("hibernate.hbm2ddl.delimiter", ";");
        var registry=new StandardServiceRegistryBuilder().applySettings(settings).build();
        try {
            var scanner=new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            var sources=new MetadataSources(registry);
            for(var bean:scanner.findCandidateComponents("org.example.meeter"))
                sources.addAnnotatedClass(Class.forName(bean.getBeanClassName()));
            var metadata=sources.buildMetadata();
            SchemaManagementToolCoordinator.process(metadata,registry,settings,action -> {
                throw new IllegalStateException("No database drop is allowed during schema export");
            });
            assertTrue(Files.readString(destination).contains("create table app_news"));
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
