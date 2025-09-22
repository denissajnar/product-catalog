package com.albert.catalog.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration


@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties::class)
class CacheConfig(private val cacheProperties: CacheProperties) {

    private val logger = LoggerFactory.getLogger(CacheConfig::class.java)

    @Bean
    fun redisCacheManagerBuilderCustomizer(): RedisCacheManagerBuilderCustomizer =
        RedisCacheManagerBuilderCustomizer { builder ->
            logger.info("Configuring cache TTL values - Products: ${cacheProperties.products.ttl}, ProductPages: ${cacheProperties.productPages.ttl}")
            builder
                .withCacheConfiguration(
                    "products",
                    RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(cacheProperties.products.ttl)
                        .disableCachingNullValues(),
                )
                .withCacheConfiguration(
                    "productPages",
                    RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(cacheProperties.productPages.ttl)
                        .disableCachingNullValues(),
                )
        }
}
