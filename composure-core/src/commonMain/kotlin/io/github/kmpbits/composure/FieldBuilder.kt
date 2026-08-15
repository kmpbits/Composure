package io.github.kmpbits.composure

import kotlin.jvm.JvmName

/**
 * Controls when sync validators run for a field.
 */
enum class ValidationTrigger {
    /** Validate on every keystroke. */
    ON_CHANGE,

    /** Validate when the field loses focus. */
    ON_BLUR,

    /** Validate only on form submit. */
    ON_SUBMIT,
}

/**
 * Builder for a single field's validation rules.
 *
 * [T] is the [FieldType] — different types expose different extension
 * functions, so the DSL is contextually aware at compile time.
 *
 * Use [addValidator] and [setAsyncValidator] inside extension functions
 * to create validators for your own custom field types:
 *
 * ```kotlin
 * fun FieldBuilder<CreditCard>.luhnCheck(message: String = "Invalid card number") =
 *     addValidator { value ->
 *         if (luhn(value)) ValidationResult.Valid
 *         else ValidationResult.Invalid(message)
 *     }
 * ```
 */
/**
 * A validator that reads the current value of another [FieldState] at validation time.
 * [FormScope] tracks these to re-run validation on this field whenever [dependency] changes.
 */
internal class DependentValidator(
    internal val dependency: FieldState<*>,
    private val block: (myValue: String, depValue: String) -> ValidationResult,
) : FieldValidator {
    override fun validate(value: String): ValidationResult =
        block(value, dependency._value.value)
}

@ComposureDsl
class FieldBuilder<T : FieldType> internal constructor(internal val type: T) {

    internal val validators                = mutableListOf<FieldValidator>()
    internal val dependentValidators       = mutableListOf<DependentValidator>()
    internal val overrideDefaultValidators = mutableListOf<FieldValidator>()
    internal var asyncValidator: AsyncFieldValidator? = null
    internal var trigger: ValidationTrigger = ValidationTrigger.ON_CHANGE
    internal var initialValue: String = ""
    internal var isOptional: Boolean = false

    /** Pre-fill the field (e.g. when editing an existing entity). */
    fun initialValue(value: String) {
        initialValue = value
    }

    /** Override when validation fires. Defaults to [ValidationTrigger.ON_CHANGE]. */
    fun trigger(t: ValidationTrigger) {
        trigger = t
    }

    /**
     * Marks this field as optional — all validators (including the type's built-in ones)
     * are skipped when the value is blank. If the user types something, full validation runs.
     *
     * ```kotlin
     * val website = scope.field(Email) { optional() }  // valid when empty, validated when filled
     * ```
     */
    fun optional() {
        isOptional = true
    }

    /**
     * Add a synchronous [FieldValidator] to this field.
     * Use this inside extension functions for custom field types.
     */
    fun addValidator(v: FieldValidator) {
        validators += v
    }

    /**
     * Add a synchronous validator defined as a lambda.
     * Use this inside extension functions for custom field types.
     */
    fun addValidator(block: (String) -> ValidationResult) {
        validators += FieldValidator(block)
    }

    /**
     * Set the async validator for this field.
     * Use this inside extension functions for custom field types.
     */
    fun setAsyncValidator(v: AsyncFieldValidator) {
        asyncValidator = v
    }

    /**
     * Set the async validator as a suspending lambda.
     * Use this inside extension functions for custom field types.
     */
    fun setAsyncValidator(block: suspend (String) -> ValidationResult) {
        asyncValidator = AsyncFieldValidator(block)
    }

    /**
     * Validate this field against another field's current value.
     * [FormScope] automatically re-runs this validator whenever [other] changes,
     * so the error stays in sync without any extra wiring.
     *
     * ```kotlin
     * val password = scope.field(Password) { minLength(8) }
     * val confirm  = scope.field(Password) {
     *     dependsOn(password) { my, theirs ->
     *         if (my == theirs) ValidationResult.Valid
     *         else ValidationResult.Invalid("Passwords do not match")
     *     }
     * }
     * ```
     */
    fun dependsOn(
        other: FieldState<*>,
        validate: (myValue: String, otherValue: String) -> ValidationResult,
    ) {
        dependentValidators += DependentValidator(other, validate)
    }
}

@DslMarker
annotation class ComposureDsl

// ---------------------------------------------------------------------------
// Email extensions
// ---------------------------------------------------------------------------

/**
 * Attach an async validator to an email field — typically a server-side
 * uniqueness check. Only fires after required + email format pass,
 * so the API never receives a malformed address.
 */
@JvmName("asyncEmail")
fun FieldBuilder<Email>.async(validator: AsyncFieldValidator) = setAsyncValidator(validator)

@JvmName("asyncEmailBlock")
fun FieldBuilder<Email>.async(block: suspend (String) -> ValidationResult) =
    setAsyncValidator(block)

// ---------------------------------------------------------------------------
// Password extensions
// ---------------------------------------------------------------------------

@JvmName("minLengthPassword")
fun FieldBuilder<Password>.minLength(
    min: Int,
    message: String = "Must be at least $min characters"
) =
    addValidator(io.github.kmpbits.composure.minLength(min, message))

@JvmName("hasUppercasePassword")
fun FieldBuilder<Password>.hasUppercase(message: String = "Must contain at least one uppercase letter") =
    addValidator(io.github.kmpbits.composure.hasUppercase(message))

@JvmName("hasDigitPassword")
fun FieldBuilder<Password>.hasDigit(message: String = "Must contain at least one number") =
    addValidator(io.github.kmpbits.composure.hasDigit(message))

@JvmName("hasSpecialCharPassword")
fun FieldBuilder<Password>.hasSpecialChar(message: String = "Must contain at least one special character") =
    addValidator(io.github.kmpbits.composure.hasSpecialChar(message))

/**
 * Validates that this field's value matches [other] — the common "confirm password" case.
 * The error re-triggers automatically when [other] changes.
 *
 * ```kotlin
 * val password = scope.field(Password) { minLength(8) }
 * val confirm  = scope.field(Password) { mustMatch(password) }
 * ```
 */
@JvmName("mustMatchPassword")
fun FieldBuilder<Password>.mustMatch(
    other: FieldState<Password>,
    message: String = "Passwords do not match",
) = dependsOn(other) { my, theirs ->
    if (my == theirs) ValidationResult.Valid else ValidationResult.Invalid(message)
}

// ---------------------------------------------------------------------------
// Default-validator message overrides
// ---------------------------------------------------------------------------

/**
 * Replaces the default error messages for [Email]'s built-in validators
 * ([required] and [email] format), without touching any extra validators you add.
 *
 * ```kotlin
 * val email = scope.field(Email) {
 *     messages(required = "Email is required", format = "Doesn't look like an email")
 * }
 * ```
 */
@JvmName("messagesEmail")
fun FieldBuilder<Email>.messages(
    required: String = "This field is required",
    format:   String = "Enter a valid email address",
) {
    overrideDefaultValidators.clear()
    overrideDefaultValidators += io.github.kmpbits.composure.required(required)
    overrideDefaultValidators += email(format)
}

/**
 * Replaces the default error message for [Password]'s built-in [required] validator.
 *
 * ```kotlin
 * val password = scope.field(Password) {
 *     messages(required = "Password is required")
 *     minLength(8)
 * }
 * ```
 */
@JvmName("messagesPassword")
fun FieldBuilder<Password>.messages(required: String = "This field is required") {
    overrideDefaultValidators.clear()
    overrideDefaultValidators += io.github.kmpbits.composure.required(required)
}

@JvmName("messagesName")
fun FieldBuilder<Name>.messages(required: String = "This field is required") {
    overrideDefaultValidators.clear()
    overrideDefaultValidators += io.github.kmpbits.composure.required(required)
}

@JvmName("messagesPhone")
fun FieldBuilder<Phone>.messages(required: String = "This field is required") {
    overrideDefaultValidators.clear()
    overrideDefaultValidators += io.github.kmpbits.composure.required(required)
}

// ---------------------------------------------------------------------------
// Name extensions
// ---------------------------------------------------------------------------

@JvmName("maxLengthName")
fun FieldBuilder<Name>.maxLength(max: Int, message: String = "Must be at most $max characters") =
    addValidator(io.github.kmpbits.composure.maxLength(max, message))

// ---------------------------------------------------------------------------
// Phone extensions
// ---------------------------------------------------------------------------

@JvmName("minDigitsPhone")
fun FieldBuilder<Phone>.minDigits(min: Int, message: String = "Enter at least $min digits") =
    addValidator { value ->
        val digits = value.filter { it.isDigit() }
        if (digits.length >= min) ValidationResult.Valid
        else ValidationResult.Invalid(message)
    }

// ---------------------------------------------------------------------------
// Text extensions — fully open
// ---------------------------------------------------------------------------

@JvmName("requiredText")
fun FieldBuilder<Text>.required(message: String = "This field is required") =
    addValidator(io.github.kmpbits.composure.required(message))

@JvmName("minLengthText")
fun FieldBuilder<Text>.minLength(min: Int, message: String = "Must be at least $min characters") =
    addValidator(io.github.kmpbits.composure.minLength(min, message))

@JvmName("maxLengthText")
fun FieldBuilder<Text>.maxLength(max: Int, message: String = "Must be at most $max characters") =
    addValidator(io.github.kmpbits.composure.maxLength(max, message))

@JvmName("matchesText")
fun FieldBuilder<Text>.matches(pattern: Regex, message: String = "Invalid format") =
    addValidator(io.github.kmpbits.composure.matches(pattern, message))

@JvmName("asyncText")
fun FieldBuilder<Text>.async(validator: AsyncFieldValidator) = setAsyncValidator(validator)

@JvmName("asyncTextBlock")
fun FieldBuilder<Text>.async(block: suspend (String) -> ValidationResult) = setAsyncValidator(block)
