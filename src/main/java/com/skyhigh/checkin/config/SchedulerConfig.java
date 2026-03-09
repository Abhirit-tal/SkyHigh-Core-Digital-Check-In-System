package com.skyhigh.checkin.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configures distributed scheduler locking via ShedLock + Redis.
 * Ensures that when the application is scaled horizontally (multiple instances),
 * each @Scheduled task runs on only ONE instance at a time.
 *
 * Default lock duration: at most 5 minutes (safety net if instance dies mid-execution).
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "5m")
public class SchedulerConfig {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "skyhigh-checkin");
    }
}

