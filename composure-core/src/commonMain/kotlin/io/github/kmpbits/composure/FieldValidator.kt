package io.github.kmpbits.composure

/**
 * Synchronous validator for a field value. Lives in the domain layer —
 * no Compose or platform dependencies.
 */
fun interface FieldValidator {
    fun validate(value: String): ValidationResult
}

/**
 * Asynchronous validator (e.g. checking email availability via an API).
 * Implement this in your domain/data layer and inject it into the form.
 * Only fires after all sync validators have passed, so the API is never
 * called with a malformed value.
 */
fun interface AsyncFieldValidator {
    suspend fun validate(value: String): ValidationResult
}

// ---------------------------------------------------------------------------
// General validators
// ---------------------------------------------------------------------------

fun required(message: String = "This field is required"): FieldValidator =
    FieldValidator { value ->
        if (value.isNotBlank()) ValidationResult.Valid
        else ValidationResult.Invalid(message)
    }

fun email(message: String = "Enter a valid email address"): FieldValidator =
    FieldValidator { value ->
        // RFC 5321 local part + proper domain label validation (no leading/trailing hyphens).
        val emailRegex =
            Regex("""^[A-Za-z0-9.!#${'$'}%&'*+/=?^_`{|}~-]+@[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\.[A-Za-z]{2,}$""")
        if (value.matches(emailRegex)) ValidationResult.Valid
        else ValidationResult.Invalid(message)
    }

fun minLength(min: Int, message: String = "Must be at least $min characters"): FieldValidator =
    FieldValidator { value ->
        if (value.length >= min) ValidationResult.Valid
        else ValidationResult.Invalid(message)
    }

fun maxLength(max: Int, message: String = "Must be at most $max characters"): FieldValidator =
    FieldValidator { value ->
        if (value.length <= max) ValidationResult.Valid
        else ValidationResult.Invalid(message)
    }

fun matches(pattern: Regex, message: String = "Invalid format"): FieldValidator =
    FieldValidator { value ->
        if (value.matches(pattern)) ValidationResult.Valid
        else ValidationResult.Invalid(message)
    }

// ---------------------------------------------------------------------------
// Password-specific validators
// ---------------------------------------------------------------------------

fun hasUppercase(message: String = "Must contain at least one uppercase letter"): FieldValidator =
    FieldValidator { value ->
        if (value.any { it.isUpperCase() }) ValidationResult.Valid
        else ValidationResult.Invalid(message)
    }

fun hasDigit(message: String = "Must contain at least one number"): FieldValidator =
    FieldValidator { value ->
        if (value.any { it.isDigit() }) ValidationResult.Valid
        else ValidationResult.Invalid(message)
    }

fun hasSpecialChar(message: String = "Must contain at least one special character"): FieldValidator =
    FieldValidator { value ->
        if (value.any { !it.isLetterOrDigit() }) ValidationResult.Valid
        else ValidationResult.Invalid(message)
    }
