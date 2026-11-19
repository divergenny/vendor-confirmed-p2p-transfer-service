package uz.hayotbank.p2ptransfer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uz.hayotbank.p2ptransfer.security.TokenService;

import java.math.BigDecimal;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

/**
 * Base class for tests that need the whole application: real Postgres in Docker,
 * a fake vendor (WireMock) and MockMvc.
 * All subclasses share one Spring context, so the database and the fake vendor start only once per run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String VENDOR_PAYMENTS = "/api/v1/payments";

    protected static final WireMockServer vendor = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        vendor.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private TokenService tokenService;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("vendor.url", vendor::baseUrl);
        // no test waits for this timeout ("no answer" is a 503 here), it only has to leave room for a slow CI machine
        registry.add("vendor.read-timeout", () -> "5s");
        registry.add("vendor.retry-delay", () -> "10ms");
        // the retry job is called by hand in its own test
        registry.add("transfers.pending-retry.enabled", () -> "false");
    }

    @BeforeEach
    void resetVendor() {
        vendor.resetAll();
    }

    protected void vendorAnswers(String status) {
        vendor.stubFor(post(VENDOR_PAYMENTS).willReturn(vendorAnswer(status)));
    }

    protected void vendorFailsWith(int httpStatus) {
        vendor.stubFor(post(VENDOR_PAYMENTS).willReturn(aResponse().withStatus(httpStatus)));
    }

    protected void vendorFailsOnceThenAnswers(String status) {
        vendor.stubFor(post(VENDOR_PAYMENTS).inScenario("busy vendor")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("vendor is back"));
        vendor.stubFor(post(VENDOR_PAYMENTS).inScenario("busy vendor")
                .whenScenarioStateIs("vendor is back")
                .willReturn(vendorAnswer(status)));
    }

    protected Long createUser(String balance) {
        String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
                "INSERT INTO users (username, full_name, balance) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, "Test User", new BigDecimal(balance));
    }

    protected BigDecimal balanceOf(Long userId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM users WHERE id = ?", BigDecimal.class, userId);
    }

    // the Authorization header of a user who has logged in
    protected String bearer(Long userId) {
        return "Bearer " + tokenService.createToken(userId, "user-" + userId).accessToken();
    }

    // like the real vendor, the stub answers with the reference it got in the request
    private static ResponseDefinitionBuilder vendorAnswer(String status) {
        return okJson("""
                {"reference": "{{jsonPath request.body '$.reference'}}", "status": "%s"}
                """.formatted(status))
                .withTransformers("response-template");
    }
}
