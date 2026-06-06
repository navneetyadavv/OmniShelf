package com.omnishelf.engine;

import com.omnishelf.engine.service.BillNumberGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class BillNumberGeneratorTest {

    private final BillNumberGenerator gen = new BillNumberGenerator();

    @Test
    void generatesSequentialNumbers() {
        String first  = gen.next();
        String second = gen.next();
        String third  = gen.next();

        assertThat(first).endsWith("-0001");
        assertThat(second).endsWith("-0002");
        assertThat(third).endsWith("-0003");
    }

    @Test
    void includesCurrentDate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(gen.next()).contains(today);
    }

    @Test
    void startsWithOmniPrefix() {
        assertThat(gen.next()).startsWith("OMNI-");
    }
}
