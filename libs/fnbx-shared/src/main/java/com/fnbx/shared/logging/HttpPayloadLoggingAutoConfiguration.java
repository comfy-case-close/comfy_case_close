package com.fnbx.shared.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnBean(ObjectMapper.class)
@EnableConfigurationProperties(PayloadLoggingProperties.class)
public class HttpPayloadLoggingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public HttpPayloadLogger httpPayloadLogger(ObjectMapper mapper, PayloadLoggingProperties properties) {
        return new HttpPayloadLogger(mapper, properties);
    }
}
