package com.acme.biz.web.servlet.circuitbreaker.config;

import com.acme.biz.web.servlet.filter.GlobalCircuitBreakerFilter;
import io.github.resilience4j.circuitbreaker.autoconfigure.CircuitBreakerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.DispatcherType;

/**
 * @author ：junsenfu
 * @date ：Created in 2022/11/2 9:11
 * 文件说明： </p>
 */
@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class WebMvcBreakerConfiguration {
    private final CircuitBreakerProperties properties;

    public WebMvcBreakerConfiguration(CircuitBreakerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public FilterRegistrationBean<GlobalCircuitBreakerFilter> breakerFilterBean() {
        GlobalCircuitBreakerFilter filter = new GlobalCircuitBreakerFilter();
        filter.setProperties(this.properties);
        FilterRegistrationBean<GlobalCircuitBreakerFilter> breakerFilterBean = new FilterRegistrationBean<>(filter);
        breakerFilterBean.setDispatcherTypes(DispatcherType.REQUEST);
        breakerFilterBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return breakerFilterBean;
    }
}
