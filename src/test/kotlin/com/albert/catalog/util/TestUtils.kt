package com.albert.catalog.util

import io.restassured.response.ExtractableResponse
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification

fun RequestSpecification.whenever(): RequestSpecification = this.`when`()

inline fun <reified T> ExtractableResponse<Response>.takeAs(): T = this.`as`(T::class.java)
