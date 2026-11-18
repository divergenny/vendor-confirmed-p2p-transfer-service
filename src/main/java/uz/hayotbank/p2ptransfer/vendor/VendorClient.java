package uz.hayotbank.p2ptransfer.vendor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.UUID;

/**
 * Asks the vendor to confirm a transfer: POST {vendor.url}/api/v1/payments.
 * Temporary failures are retried with the same reference. That is safe: for a reference
 * it has already seen, the vendor does not create a new operation and returns the first result.
 */
@Slf4j
@Component
public class VendorClient {

    private final RestClient restClient;
    private final VendorProperties properties;

    public VendorClient(VendorProperties properties) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .build();
    }

    public PaymentStatus confirmPayment(UUID reference, Long senderId, Long recipientId, BigDecimal amount) {
        PaymentRequest request = new PaymentRequest(reference, String.valueOf(senderId), String.valueOf(recipientId), amount);

        for (int attempt = 1; ; attempt++) {
            try {
                PaymentResponse response = restClient.post()
                        .uri("/api/v1/payments")
                        .body(request)
                        .retrieve()
                        .body(PaymentResponse.class);
                return checkAnswer(reference, response);
            } catch (HttpClientErrorException e) {
                // 4xx: the request itself is wrong, sending it again won't help
                throw new VendorRejectedRequestException(reference, e.getStatusCode().value(), e.getResponseBodyAsString());
            } catch (HttpServerErrorException | ResourceAccessException e) {
                // 5xx, timeout or no connection: temporary, try again with the same reference
                log.warn("Vendor call for transfer {} failed, attempt {} of {}: {}",
                        reference, attempt, properties.maxAttempts(), e.getMessage());
                if (attempt >= properties.maxAttempts()) {
                    throw new VendorUnavailableException(reference, e);
                }
                pauseBeforeRetry(reference, attempt);
            } catch (RestClientException e) {
                // an answer we could not read: we don't know what the vendor decided
                throw new VendorUnavailableException(reference, e);
            }
        }
    }

    private PaymentStatus checkAnswer(UUID reference, PaymentResponse response) {
        if (response == null || response.status() == null || !reference.equals(response.reference())) {
            throw new VendorUnavailableException(reference, String.valueOf(response));
        }
        log.info("Vendor answered {} for transfer {}", response.status(), reference);
        return response.status();
    }

    private void pauseBeforeRetry(UUID reference, int attempt) {
        try {
            // the pause grows with every attempt: 1x, 2x, ...
            Thread.sleep(properties.retryDelay().multipliedBy(attempt).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VendorUnavailableException(reference, e);
        }
    }
}
