package com.albert.catalog

import com.albert.catalog.config.TestcontainersConfiguration
import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<CatalogApplication>().with(TestcontainersConfiguration::class).run(*args)
}
