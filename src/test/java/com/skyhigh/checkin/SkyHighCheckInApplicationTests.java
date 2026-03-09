package com.skyhigh.checkin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {RabbitAutoConfiguration.class, RedisAutoConfiguration.class})
class SkyHighCheckInApplicationTests {

    @Test
    void contextLoads() {
        // Basic context load test — verifies Spring wiring without external dependencies
    }
}

