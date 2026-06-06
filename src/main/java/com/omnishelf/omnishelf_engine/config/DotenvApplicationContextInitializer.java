package com.omnishelf.engine.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class DotenvApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Path dotenvPath = Path.of(".env");
        if (!Files.exists(dotenvPath)) return;

        try {
            Map<String, Object> properties = new LinkedHashMap<>();
            for (String line : Files.readAllLines(dotenvPath)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int idx = trimmed.indexOf('=');
                if (idx <= 0) continue;
                String key   = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'")  && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                properties.put(key, value);
            }
            MutablePropertySources sources = applicationContext.getEnvironment().getPropertySources();
            sources.addLast(new MapPropertySource("dotenvProperties", properties));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load .env file", ex);
        }
    }
}
