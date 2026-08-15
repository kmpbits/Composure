package io.github.kmpbits.composure

/**
 * The result of validating a field's value.
 */
sealed class ValidationResult {

    /** The value passed validation. */
    data object Valid : ValidationResult()

    /** The value failed validation with a human-readable [message]. */
    data class Invalid(val message: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
}
