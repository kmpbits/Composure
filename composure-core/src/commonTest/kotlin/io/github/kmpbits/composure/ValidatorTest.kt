package io.github.kmpbits.composure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidatorTest {

    @Test
    fun `required passes for non-blank value`() {
        val result = required().validate("hello")
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `required fails for blank value`() {
        val result = required().validate("   ")
        assertIs<ValidationResult.Invalid>(result)
    }

    @Test
    fun `email passes for valid address`() {
        val result = email().validate("geral@kmpbits.io")
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `email passes for plus alias`() {
        val result = email().validate("geral+other@kmpbits.io")
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `email passes for subdomain`() {
        val result = email().validate("geral@mail.kmpbits.io")
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `email fails for missing at-sign`() {
        val result = email().validate("notanemail")
        assertIs<ValidationResult.Invalid>(result)
    }

    @Test
    fun `email fails for domain label starting with hyphen`() {
        val result = email().validate("geral@-kmpbits.io")
        assertIs<ValidationResult.Invalid>(result)
    }

    @Test
    fun `email fails for missing TLD`() {
        val result = email().validate("geral@kmpbits")
        assertIs<ValidationResult.Invalid>(result)
    }

    @Test
    fun `minLength passes when value is long enough`() {
        val result = minLength(8).validate("password")
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `minLength fails when value is too short`() {
        val result = minLength(8).validate("pass")
        assertIs<ValidationResult.Invalid>(result)
        assertEquals("Must be at least 8 characters", result.message)
    }

    @Test
    fun `maxLength passes when value is within limit`() {
        val result = maxLength(5).validate("hi")
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `maxLength fails when value exceeds limit`() {
        val result = maxLength(5).validate("toolong")
        assertIs<ValidationResult.Invalid>(result)
    }
}
