package org.example.meeter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:context-tests;DB_CLOSE_DELAY=-1",
    "app.dev.bootstrap=false",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MeeterApplicationTests {

	@Test
	void contextLoads() {
	}

}
