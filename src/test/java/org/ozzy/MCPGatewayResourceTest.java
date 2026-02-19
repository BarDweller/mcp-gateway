package org.ozzy;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TestConfigProfile.class)
class MCPGatewayResourceTest {

    @BeforeEach
    void cleanProperties() throws Exception {
        Files.deleteIfExists(Path.of(TestConfigProfile.TEST_PROPERTIES_PATH));
    }

    private String authHeader() {
        String token = Base64.getEncoder().encodeToString("admin:admin".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    @Test
    void createAndListGateway() {
        String payload = "{" +
            "\"name\":\"Gateway One\"," +
            "\"host\":\"localhost\"," +
            "\"port\":8888," +
            "\"authType\":\"BASIC\"," +
            "\"authUsername\":\"admin\"," +
            "\"authPassword\":\"secret\"," +
            "\"tools\":[{" +
            "\"serverId\":\"server-1\"," +
            "\"toolName\":\"tool-1\"," +
            "\"validationMode\":\"PER_TIME_PERIOD\"," +
            "\"validationPeriodSeconds\":300" +
            "}]" +
            "}";

        String id = given()
            .header("Authorization", authHeader())
            .contentType("application/json")
            .body(payload)
            .when()
            .post("/mcp-gateways")
            .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("name", equalTo("Gateway One"))
            .extract()
            .path("id");

        given()
            .header("Authorization", authHeader())
            .when()
            .get("/mcp-gateways")
            .then()
            .statusCode(200)
            .body(String.format("find { it.id == '%s' }.id", id), equalTo(id))
                .body(String.format("find { it.id == '%s' }.authType", id), equalTo("BASIC"))
                .body(String.format("find { it.id == '%s' }.authUsername", id), equalTo("admin"))
            .body(String.format("find { it.id == '%s' }.tools[0].validationMode", id), equalTo("PER_TIME_PERIOD"))
            .body(String.format("find { it.id == '%s' }.tools[0].validationPeriodSeconds", id), equalTo(300));
    }

    @Test
    void updateGatewayName() {
        String payload = "{\"name\":\"Gateway One\",\"host\":\"localhost\",\"port\":8888}";

        String id = given()
            .header("Authorization", authHeader())
            .contentType("application/json")
            .body(payload)
            .when()
            .post("/mcp-gateways")
            .then()
            .statusCode(200)
            .extract()
            .path("id");

        String updatePayload = "{\"id\":\"" + id + "\",\"name\":\"Gateway Updated\",\"host\":\"localhost\",\"port\":8888,\"status\":\"STOPPED\"}";

        given()
            .header("Authorization", authHeader())
            .contentType("application/json")
            .body(updatePayload)
            .when()
            .put("/mcp-gateways/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("Gateway Updated"));
    }
}
