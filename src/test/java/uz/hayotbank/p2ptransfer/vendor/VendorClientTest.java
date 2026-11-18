package uz.hayotbank.p2ptransfer.vendor;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VendorClientTest {

    private static final String PAYMENTS = "/api/v1/payments";

    @RegisterExtension
    static WireMockExtension vendor = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final UUID reference = UUID.fromString("7f3c9a2e-5b1d-4e8a-9c6f-2d4b8e1a0f35");
    private final BigDecimal amount = new BigDecimal("150000.00");

    private VendorClient client;

    @BeforeEach
    void setUp() {
        // short timeouts and pauses, so the tests take seconds and not minutes
        client = clientFor(vendor.baseUrl());
    }

    @Test
    void sendsTransferInVendorFormat() {
        vendor.stubFor(post(PAYMENTS).willReturn(okJson(answer("APPROVED"))));

        PaymentStatus status = client.confirmPayment(reference, 101L, 102L, amount);

        assertThat(status).isEqualTo(PaymentStatus.APPROVED);
        vendor.verify(postRequestedFor(urlEqualTo(PAYMENTS))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("""
                        {"reference": "7f3c9a2e-5b1d-4e8a-9c6f-2d4b8e1a0f35", "payer": "101", "payee": "102", "amount": 150000.00}
                        """)));
    }

    @Test
    void returnsDeclined() {
        vendor.stubFor(post(PAYMENTS).willReturn(okJson(answer("DECLINED"))));

        assertThat(client.confirmPayment(reference, 1L, 2L, amount)).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    void retriesTemporaryErrorWithSameReference() {
        vendor.stubFor(post(PAYMENTS).inScenario("vendor is busy")
                .whenScenarioStateIs(STARTED)
                .willReturn(serviceUnavailable())
                .willSetStateTo("vendor is back"));
        vendor.stubFor(post(PAYMENTS).inScenario("vendor is busy")
                .whenScenarioStateIs("vendor is back")
                .willReturn(okJson(answer("APPROVED"))));

        PaymentStatus status = client.confirmPayment(reference, 1L, 2L, amount);

        assertThat(status).isEqualTo(PaymentStatus.APPROVED);
        vendor.verify(2, postRequestedFor(urlEqualTo(PAYMENTS))
                .withRequestBody(matchingJsonPath("$.reference", equalTo(reference.toString()))));
    }

    @Test
    void givesUpAfterLastAttempt() {
        vendor.stubFor(post(PAYMENTS).willReturn(serverError()));

        assertThatThrownBy(() -> client.confirmPayment(reference, 1L, 2L, amount))
                .isInstanceOf(VendorUnavailableException.class);
        vendor.verify(3, postRequestedFor(urlEqualTo(PAYMENTS)));
    }

    @Test
    void tooSlowAnswerIsRetriedLikeTemporaryError() {
        vendor.stubFor(post(PAYMENTS).willReturn(okJson(answer("APPROVED")).withFixedDelay(1000)));

        assertThatThrownBy(() -> client.confirmPayment(reference, 1L, 2L, amount))
                .isInstanceOf(VendorUnavailableException.class);
        vendor.verify(3, postRequestedFor(urlEqualTo(PAYMENTS)));
    }

    @Test
    void vendorIsDown() {
        VendorClient clientWithoutVendor = clientFor("http://localhost:1");

        assertThatThrownBy(() -> clientWithoutVendor.confirmPayment(reference, 1L, 2L, amount))
                .isInstanceOf(VendorUnavailableException.class);
    }

    @Test
    void badRequestIsNotRetried() {
        vendor.stubFor(post(PAYMENTS).willReturn(badRequest().withBody("amount must be positive")));

        assertThatThrownBy(() -> client.confirmPayment(reference, 1L, 2L, amount))
                .isInstanceOf(VendorRejectedRequestException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("amount must be positive");
        vendor.verify(1, postRequestedFor(urlEqualTo(PAYMENTS)));
    }

    @Test
    void answerForAnotherReferenceIsNotTrusted() {
        vendor.stubFor(post(PAYMENTS).willReturn(okJson("""
                {"reference": "00000000-0000-0000-0000-000000000000", "status": "APPROVED"}
                """)));

        assertThatThrownBy(() -> client.confirmPayment(reference, 1L, 2L, amount))
                .isInstanceOf(VendorUnavailableException.class);
    }

    private VendorClient clientFor(String url) {
        return new VendorClient(new VendorProperties(url, Duration.ofMillis(500), Duration.ofMillis(300), 3, Duration.ofMillis(10)));
    }

    private String answer(String status) {
        return """
                {"reference": "%s", "status": "%s"}
                """.formatted(reference, status);
    }
}
