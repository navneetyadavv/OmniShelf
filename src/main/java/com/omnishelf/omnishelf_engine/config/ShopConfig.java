package com.omnishelf.engine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "shop")
@Data
public class ShopConfig {
    private String name;
    private String address;
    private String phone;
    private String gstin;
}
