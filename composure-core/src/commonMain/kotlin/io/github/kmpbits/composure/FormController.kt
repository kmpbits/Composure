package io.github.kmpbits.composure

import kotlinx.coroutines.flow.StateFlow

/**
 * The form-level control surface — submit, reset, validity, and loading state.
 * All observable properties are [StateFlow]s, consumable from Compose and SwiftUI.
 *
 * [FormScope] implements this interface. Your form class can delegate to it
 * with Kotlin's `by` syntax so callers get a single unified object:
 *
 * ```kotlin
 * class RegistrationForm(scope: FormScope) : FormController by scope {
 *     val email    = scope.field(Email) { async(checkEmailUseCase) }
 *     val password = scope.field(Password) { minLength(8) }
 *     val confirm  = scope.field(Password)
 * }
 *
 * val form = rememberFormState { scope -> RegistrationForm(scope) }
 *
 * // Compose
 * val isValid by form.isValid.collectAsState()
 * Button(
 *     onClick = form.handleSubmit { values -> viewModel.register(values) },
 *     enabled = isValid && !form.isSubmitting.value,
 * )
 * ```
 */
interface FormController {

    /** True when every field is valid and no async validation is in flight. */
    val isValid: StateFlow<Boolean>

    /** True when any field has been changed from its initial value. */
    val isDirty: StateFlow<Boolean>

    /** True while the submit handler is executing. */
    val isSubmitting: StateFlow<Boolean>

    /**
     * Error set by the submit handler (e.g. "Incorrect password").
     * Reset automatically on the next submit attempt.
     */
    val submitError: StateFlow<String?>

    /**
     * Returns a lambda suitable for a Button's `onClick`.
     * Touches all fields (revealing any hidden errors), validates everything,
     * and calls [onValid] only if the form is fully valid.
     */
    fun handleSubmit(onValid: suspend (values: Map<String, String>) -> Unit): () -> Unit

    /** Resets all fields to their initial values and clears all errors. */
    fun reset()
}
