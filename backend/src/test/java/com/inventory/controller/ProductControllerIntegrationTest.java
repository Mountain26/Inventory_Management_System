package com.inventory.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
})
@org.springframework.test.context.ActiveProfiles("test")
class ProductControllerIntegrationTest {

    private static final String externalJdbcUrl = System.getenv("MYSQL_TEST_URL");
    private static MySQLContainer<?> mysql;

    @BeforeAll
    static void prepareMySql() {
        if (externalJdbcUrl != null && !externalJdbcUrl.isBlank()) {
            return; // GitHub Actions supplies an isolated, healthy MySQL service.
        }
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "MySQL integration tests require MYSQL_TEST_URL or a running Docker engine");
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("ims_test").withUsername("test").withPassword("test");
        mysql.start();
    }

    @AfterAll
    static void stopLocalMySql() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        if (externalJdbcUrl != null && !externalJdbcUrl.isBlank()) {
            properties.add("spring.datasource.url", () -> externalJdbcUrl);
            properties.add("spring.datasource.username", () -> requiredEnvironment("MYSQL_TEST_USERNAME"));
            properties.add("spring.datasource.password", () -> requiredEnvironment("MYSQL_TEST_PASSWORD"));
        } else {
            properties.add("spring.datasource.url", mysql::getJdbcUrl);
            properties.add("spring.datasource.username", mysql::getUsername);
            properties.add("spring.datasource.password", mysql::getPassword);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required when MYSQL_TEST_URL is set");
        }
        return value;
    }

    @Autowired
    private TestRestTemplate restTemplate;

    private String getAuthToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String email = "staff-" + java.util.UUID.randomUUID() + "@test.local";
        Map<String, String> registerBody = Map.of(
                "fullName", "Test Staff",
                "email", email,
                "password", "password123"
        );
        var registration = restTemplate.postForEntity("/api/auth/register", new HttpEntity<>(registerBody, headers), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Login
        Map<String, String> loginBody = Map.of(
                "email", email,
                "password", "password123"
        );
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginBody, headers), Map.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).containsKey("token");

        return (String) loginResponse.getBody().get("token");
    }

    @Test
    void getProducts_authenticated_returns200() {
        String token = getAuthToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Object[]> response = restTemplate.exchange(
                "/api/products", HttpMethod.GET, new HttpEntity<>(headers), Object[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void getProducts_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/products", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getProductByBarcode_notFound_returns400() {
        String token = getAuthToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/products/by-barcode?code=NONEXISTENT",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getLowStockProducts_returns200() {
        String token = getAuthToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Object[]> response = restTemplate.exchange(
                "/api/products/low-stock", HttpMethod.GET, new HttpEntity<>(headers), Object[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
