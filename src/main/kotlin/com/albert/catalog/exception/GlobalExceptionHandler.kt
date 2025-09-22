package com.albert.catalog.exception

import com.albert.catalog.dto.ErrorResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

@ControllerAdvice
class GlobalExceptionHandler {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "Invalid value")
        }

        log.warn { "Validation failed: $errors" }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Validation Failed",
                message = "Request validation failed",
                errors = errors,
            ),
        )
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleWebExchangeBindException(ex: WebExchangeBindException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "Invalid value")
        }

        log.warn { "Reactive validation failed: $errors" }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Validation Failed",
                message = "Request validation failed",
                errors = errors,
            ),
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val errors = ex.constraintViolations.associate { violation ->
            val propertyPath = violation.propertyPath.toString()
            val parameterName = propertyPath.substringAfterLast('.')
            parameterName to violation.message
        }

        log.warn { "Method parameter validation failed: $errors" }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Validation Failed",
                message = "Parameter validation failed",
                errors = errors,
            ),
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        log.warn { "Type mismatch for parameter '${ex.name}': ${ex.message}" }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = "Invalid parameter type for '${ex.name}'",
            ),
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn { "Illegal argument: ${ex.message}" }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = ex.message ?: "Invalid argument",
            ),
        )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFoundException(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        log.warn { "Resource not found: ${ex.message}" }

        return ResponseEntity.notFound().build()
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error(ex) { "Unexpected error occurred: ${ex.message}" }

        return ResponseEntity.internalServerError().body(
            ErrorResponse(
                timestamp = Instant.now(),
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "Internal Server Error",
                message = "An unexpected error occurred",
            ),
        )
    }
}


