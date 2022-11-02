/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.acme.biz.web.servlet.filter;


import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.autoconfigure.CircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.configure.CircuitBreakerConfiguration;
import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigurationProperties;
import io.vavr.CheckedRunnable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局 {@link CircuitBreaker} Filter
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 1.0.0
 */
public class GlobalCircuitBreakerFilter implements Filter {

    //同时也是CircuitBreakerConfig的key
    public static final String FILTER_NAME = "GlobalCircuitBreakerFilter";

    public static final String BREAKER_NAME = "CircuitBreaker-" + FILTER_NAME;


    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreakerProperties properties;

    private CompositeCustomizer<CircuitBreakerConfigCustomizer> breakerCustomizer = new CompositeCustomizer<>(Collections.EMPTY_LIST);


    public void setProperties(CircuitBreakerProperties properties) {
        this.properties = properties;
    }

    private CircuitBreakerRegistry createRegistry() {
        //兜底
        Map<String, CircuitBreakerConfigurationProperties.InstanceProperties> configs = this.properties.getConfigs();
        if (Objects.isNull(this.properties) || !configs.containsKey(FILTER_NAME)) {
            CircuitBreakerConfig defaultConfig = CircuitBreakerConfig
                    .custom()
                    .build();
            return CircuitBreakerRegistry.of(defaultConfig);
        }

        CircuitBreakerConfigurationProperties.InstanceProperties instanceProperties = configs.get(FILTER_NAME);
        CircuitBreakerConfig circuitBreakerConfig = this.properties
                .createCircuitBreakerConfig(FILTER_NAME, instanceProperties, breakerCustomizer);
        return CircuitBreakerRegistry.of(circuitBreakerConfig);
    }

    public void updateConfig() {
        CircuitBreakerConfigurationProperties.InstanceProperties instanceProperties = this.properties.
                getConfigs().get(FILTER_NAME);

        CircuitBreakerConfig circuitBreakerConfig = this.properties
                .createCircuitBreakerConfig(FILTER_NAME, instanceProperties, breakerCustomizer);
        CircuitBreaker newBreaker = circuitBreakerRegistry.circuitBreaker("NEW", circuitBreakerConfig);
       circuitBreakerRegistry.replace(BREAKER_NAME, newBreaker);
        circuitBreakerRegistry.remove("NEW");
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.circuitBreakerRegistry = createRegistry();
       this.circuitBreakerRegistry.circuitBreaker(BREAKER_NAME);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // 粗粒度的实现
        CircuitBreaker circuitBreaker = this.circuitBreakerRegistry.circuitBreaker(BREAKER_NAME);
        final long start = circuitBreaker.getCurrentTimestamp();
        try {
            chain.doFilter(request, response);
            long duration = circuitBreaker.getCurrentTimestamp() - start;
            circuitBreaker.onSuccess(duration, circuitBreaker.getTimestampUnit());
        } catch (Throwable e) {
            // Do not handle java.lang.Error
            long duration = circuitBreaker.getCurrentTimestamp() - start;
            circuitBreaker.onError(duration, circuitBreaker.getTimestampUnit(), e);
            throw e;
        }

//        try {
//            circuitBreaker.decorateCheckedRunnable(() -> chain.doFilter(request, response)).run();
//        } catch (Throwable e) {
//            throw new ServletException(e);
//        }

    }

    @Override
    public void destroy() {
    }
}
