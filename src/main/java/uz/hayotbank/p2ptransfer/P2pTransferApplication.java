package uz.hayotbank.p2ptransfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class P2pTransferApplication {

    public static void main(String[] args) {
        SpringApplication.run(P2pTransferApplication.class, args);
    }

}
