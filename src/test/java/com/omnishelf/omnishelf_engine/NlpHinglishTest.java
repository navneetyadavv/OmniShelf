package com.omnishelf.omnishelf_engine;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

import com.omnishelf.omnishelf_engine.dto.ParsedOrder;
import com.omnishelf.omnishelf_engine.dto.ParsedItem;
import com.omnishelf.omnishelf_engine.service.NlpOrchestrationService;
import com.omnishelf.omnishelf_engine.service.GeminiService;
import com.omnishelf.omnishelf_engine.service.GeminiPromptBuilder;
import com.omnishelf.omnishelf_engine.service.GeminiResponseValidator;

@SpringBootTest
class NlpHinglishTest {

    @Autowired NlpOrchestrationService nlpService;
    @Autowired GeminiService geminiService;
    @Autowired GeminiPromptBuilder promptBuilder;
    @Autowired GeminiResponseValidator validator;

    private final List<String> brands = List.of("Nike", "Adidas", "Samsung", "Apple", "Bata");
    private final List<String> categories = List.of("Footwear", "Electronics", "Apparel");

    @ParameterizedTest
    @MethodSource("hinglishTestCases")
    void shouldParseHinglishMessages(String input, String expectedBrand, int expectedQty) {
        String prompt = promptBuilder.buildExtractionPrompt(input, brands, categories);
        String json = geminiService.callGemini(prompt);
        ParsedOrder order = validator.validateAndParse(json);

        assertThat(order.getError()).isNull();
        assertThat(order.getItems()).isNotEmpty();

        ParsedItem firstItem = order.getItems().get(0);
        String resolvedBrand = firstItem.getBrand() != null
            ? firstItem.getBrand() : firstItem.getRawBrand();

        assertThat(resolvedBrand).containsIgnoringCase(expectedBrand);
        assertThat(firstItem.getQuantity()).isEqualTo(expectedQty);
    }

    static Stream<Arguments> hinglishTestCases() {
        return Stream.of(
            Arguments.of("2 Niki size 8",                        "Nike",    2),
            Arguments.of("ek adidas blue wala",                  "Adidas",  1),
            Arguments.of("teen samsung ka phone 128gb",          "Samsung", 3),
            Arguments.of("bhai ek aur nike dena",                "Nike",    1),
            Arguments.of("do jode Adiddas size saat",            "Adidas",  2),
            Arguments.of("Ramesh ke liye 1 apple watch silver",  "Apple",   1),
            Arguments.of("5 bata chappal number 9",              "Bata",    5),
            Arguments.of("chaar Samasung S24 kala wala",         "Samsung", 4)
        );
    }
}