package com.example.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 스프링 컨텍스트가 실제로 부팅되는지 확인한다.
 *
 * <p>실행 중인 PostgreSQL과 {@code JWT_SECRET}이 둘 다 있어야 컨텍스트가 뜬다. 기본으로 돌면
 * 둘 중 하나라도 없는 환경에서 {@code ./mvnw test}가 통째로 BUILD FAILURE가 되므로,
 * 두 환경변수가 모두 설정됐을 때만 켠다. 배포 전에는 반드시 한 번 돌려야 하는 테스트다.
 *
 * <pre>
 * DB_URL=jdbc:postgresql://localhost:5432/demo_db \
 *   JWT_SECRET=$(openssl rand -base64 32) ./mvnw test
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
