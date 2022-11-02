package com.acme.biz.web.servlet.circuitbreaker;

import com.acme.biz.web.servlet.filter.GlobalCircuitBreakerFilter;
import io.github.resilience4j.circuitbreaker.configure.CircuitBreakerConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ：junsenfu
 * @date ：Created in 2022/11/1 22:46
 * 文件说明： </p>
 * <p>
 * 1. 基于 GlobalCircuitBreakerFilter  实现
 * 2. 基于 resilience4j-spring-boot2 @ConfigurationProperties Bean CircuitBreakerProperties 实现
 * 3. 动态生成变更 CircuitBreaker FailureRateThreshold 规则
 * <p>
 * 1. 重建 CircuitBreaker
 * 2. 使用过去相同的 CircuitBreaker 名称
 */
@Configuration
public class DynamicCircuitBreakerConfiguration {

    private final static Set<String> INSTANCE_PROPERTIES_FILED=new HashSet<>();

    @Autowired
    private FilterRegistrationBean<GlobalCircuitBreakerFilter> filter;

    static {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(CircuitBreakerConfigurationProperties.InstanceProperties.class);
            for (PropertyDescriptor propertyDescriptor : beanInfo.getPropertyDescriptors()) {
                INSTANCE_PROPERTIES_FILED.add(propertyDescriptor.getName());
            }
        } catch (IntrospectionException e) {
            e.printStackTrace();
        }
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChangeEvent(EnvironmentChangeEvent changeEvent) {
        Set<String> keys = changeEvent.getKeys();
        for (String key : keys) {
            if (validateKey(key)) {
                filter.getFilter().updateConfig();
            }
        }
    }

    private boolean validateKey(String key) {
        String[] split = key.split("\\.");
        String value = split[split.length - 1];
        return INSTANCE_PROPERTIES_FILED.contains(value);
    }
}
