package com.example.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 스프링 컨텍스트가 실제로 부팅되는지 확인한다.
 *
 * <p>실행 중인 PostgreSQL이 필요해서 DB 없이는 컨텍스트 로딩이 실패한다. 기본으로 돌면
 * DB가 없는 환경에서 {@code ./mvnw test}가 통째로 BUILD FAILURE가 되므로,
 * {@code DB_URL}이 설정됐을 때만 켠다. 배포 전에는 반드시 한 번 돌려야 하는 테스트다.
 *
 * <pre>DB_URL=jdbc:postgresql://localhost:5432/demo_db ./mvnw test</pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
