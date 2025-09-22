package com.albert.catalog.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "spring.cache.redis")
data class CacheProperties(
    val products: CacheTtlConfig = CacheTtlConfig(),
    val productPages: CacheTtlConfig = CacheTtlConfig(),
)

data class CacheTtlConfig(
    val ttl: Duration = Duration.ofMinutes(30),
)
