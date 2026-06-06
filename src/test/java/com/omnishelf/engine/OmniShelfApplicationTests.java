package com.omnishelf.engine;

import com.omnishelf.engine.service.BillNumberGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OmniShelfApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts cleanly with H2 and stub credentials
    }
}
