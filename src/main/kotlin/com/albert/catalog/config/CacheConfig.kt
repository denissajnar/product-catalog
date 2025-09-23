package com.albert.catalog.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer


@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties::class)
class CacheConfig(private val cacheProperties: CacheProperties) {

    private val logger = LoggerFactory.getLogger(CacheConfig::class.java)

    @Bean
    fun redisCacheManagerBuilderCustomizer(): RedisCacheManagerBuilderCustomizer =
        RedisCacheManagerBuilderCustomizer { builder ->
            logger.info("Configuring cache TTL values - Products: ${cacheProperties.products.ttl}, ProductPages: ${cacheProperties.productPages.ttl}")

            val objectMapper = ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
            val cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues()
            
            builder
                .withCacheConfiguration(
                    "products",
                    cacheConfiguration
                        .entryTtl(cacheProperties.products.ttl),
                )
                .withCacheConfiguration(
                    "productPages",
                    cacheConfiguration
                        .entryTtl(cacheProperties.productPages.ttl),
                )
        }
}
