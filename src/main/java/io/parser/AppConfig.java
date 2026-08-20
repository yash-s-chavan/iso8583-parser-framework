package io.parser;

import io.builder.ISO8583Builder;
import io.config.FieldConfigurationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public FieldConfigurationManager fieldConfigurationManager() {
        // Instantiate and load your field definitions here
        return new FieldConfigurationManager();
    }

    @Bean
    public ISO8583Builder iso8583Builder(FieldConfigurationManager configManager) {
        // Spring will automatically inject the configManager bean above
        return new ISO8583Builder(configManager);
    }
}