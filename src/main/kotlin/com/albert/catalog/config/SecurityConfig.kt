package com.albert.catalog.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(): MapReactiveUserDetailsService {
        val user = User.withUsername("admin")
            .password(passwordEncoder().encode("admin123"))
            .roles("ADMIN")
            .build()

        return MapReactiveUserDetailsService(user)
    }

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/actuator/health").permitAll()
                    .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs/**").permitAll()
                    .pathMatchers("/api/v1/products/**").hasRole("ADMIN")
                    .anyExchange().authenticated()
            }
            .httpBasic { }
            .csrf { it.disable() }
            .build()
}
