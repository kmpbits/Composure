package io.github.kmpbits.composure

/**
 * Platform-agnostic hint for what kind of keyboard a field expects.
 * UI layers (Compose, SwiftUI, etc.) map this to their own keyboard types.
 */
enum class KeyboardHint {
    Text, Email, Password, Phone, Number
}

/**
 * Marker interface for field types. Implement this to create your own types
 * with custom keyboard hints, default validators, and type-safe DSL extensions.
 *
 * The library is intentionally UI-agnostic — [KeyboardHint] and [isSecret]
 * are plain Kotlin, so this works identically on Compose and SwiftUI.
 *
 * ## Defining a custom type
 * ```kotlin
 * object CreditCard : FieldType {
 *     override val keyboardHint = KeyboardHint.Number
 *     override val defaultValidators = listOf(required())
 * }
 *
 * fun FieldBuilder<CreditCard>.luhnCheck(message: String = "Invalid card number") =
 *     addValidator { value ->
 *         if (luhn(value)) ValidationResult.Valid
 *         else ValidationResult.Invalid(message)
 *     }
 * ```
 */
interface FieldType {
    /** Hints to the UI layer which keyboard type to show. */
    val keyboardHint: KeyboardHint get() = KeyboardHint.Text

    /** When true, the UI layer should mask the input (e.g. password dots). */
    val isSecret: Boolean get() = false

    /**
     * Validators applied before any user-defined ones.
     * Override to pre-bake rules for your type.
     */
    val defaultValidators: List<FieldValidator> get() = emptyList()
}

// ---------------------------------------------------------------------------
// Built-in field types
// ---------------------------------------------------------------------------

/** Email address. Pre-bakes [required] + [email] format validation. */
object Email : FieldType {
    override val keyboardHint = KeyboardHint.Email
    override val defaultValidators: List<FieldValidator>
        get() = listOf(required(), email())
}

/** Password. Pre-bakes [required]. Input is masked by default. */
object Password : FieldType {
    override val keyboardHint = KeyboardHint.Password
    override val isSecret = true
    override val defaultValidators: List<FieldValidator>
        get() = listOf(required())
}

/** Person or place name. Pre-bakes [required]. */
object Name : FieldType {
    override val defaultValidators: List<FieldValidator>
        get() = listOf(required())
}

/** Phone number. Pre-bakes [required]. */
object Phone : FieldType {
    override val keyboardHint = KeyboardHint.Phone
    override val defaultValidators: List<FieldValidator>
        get() = listOf(required())
}

/** Fully open text field — no pre-baked validators, plain keyboard. */
object Text : FieldType
