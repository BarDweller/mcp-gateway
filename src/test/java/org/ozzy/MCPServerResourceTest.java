package org.ozzy;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TestConfigProfile.class)
class MCPServerResourceTest {

    @BeforeEach
    void cleanProperties() throws Exception {
        Files.deleteIfExists(Path.of(TestConfigProfile.TEST_PROPERTIES_PATH));
    }

    private String authHeader() {
        String token = Base64.getEncoder().encodeToString("admin:admin".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    @Test
    void createAndListServer() {
        String payload = "{" +
                "\"name\":\"Server One\"," +
                "\"host\":\"localhost\"," +
                "\"port\":8080," +
                "\"type\":\"remote\"," +
            "\"protocol\":\"HTTP\"," +
            "\"remotePath\":\"/mcp\"," +
            "\"authorizationType\":\"Basic\"," +
            "\"authUsername\":\"user\"," +
            "\"authPassword\":\"pass\"," +
            "\"headers\":{\"X-Test\":\"value\"}" +
                "}";

        String id = given()
            .header("Authorization", authHeader())
            .contentType("application/json")
            .body(payload)
            .when()
            .post("/mcp-servers")
            .then()
            .statusCode(201)
            .body("name", equalTo("Server One"))
            .body("host", equalTo("localhost"))
            .extract()
            .path("id");

        given()
            .header("Authorization", authHeader())
            .when()
            .get("/mcp-servers")
            .then()
            .statusCode(200)
            .body(String.format("find { it.id == '%s' }.name", id), equalTo("Server One"))
            .body(String.format("find { it.id == '%s' }.host", id), equalTo("localhost"))
            .body(String.format("find { it.id == '%s' }.authorizationType", id), equalTo("Basic"))
            .body(String.format("find { it.id == '%s' }.authUsername", id), equalTo("user"));
    }

    @Test
    void updateServer() {
        String payload = "{" +
                "\"name\":\"Server One\"," +
                "\"host\":\"localhost\"," +
                "\"port\":8080," +
                "\"type\":\"remote\"," +
                "\"protocol\":\"HTTP\"," +
                "\"remotePath\":\"/mcp\"" +
                "}";

        String id = given()
            .header("Authorization", authHeader())
            .contentType("application/json")
            .body(payload)
            .when()
            .post("/mcp-servers")
            .then()
            .statusCode(201)
            .body("name", equalTo("Server One"))
            .extract()
            .path("id");

        String updatePayload = "{" +
                "\"name\":\"Server Updated\"," +
                "\"host\":\"localhost\"," +
                "\"port\":8081," +
                "\"type\":\"remote\"," +
                "\"protocol\":\"HTTP\"," +
                "\"remotePath\":\"/mcp\"" +
                "}";

        given()
            .header("Authorization", authHeader())
            .contentType("application/json")
            .body(updatePayload)
            .when()
            .put("/mcp-servers/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("Server Updated"))
            .body("port", equalTo(8081));
    }
}
