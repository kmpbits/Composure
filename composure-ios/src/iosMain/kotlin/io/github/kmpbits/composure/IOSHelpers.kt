package io.github.kmpbits.composure

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// ── Async-check callback (avoids KotlinUnit boxing in Swift) ──────────────

/**
 * Callback passed to [ComposureFormScope.asyncValidator].
 * Using a named interface keeps the Swift side free of [KotlinUnit].
 *
 * ```swift
 * composureScope.asyncValidator { value, callback in
 *     Task {
 *         let error = await api.check(value)
 *         callback.complete(errorMessage: error)   // returns Void, not KotlinUnit
 *     }
 * }
 * ```
 */
fun interface AsyncCheckCallback {
    fun complete(errorMessage: String?)
}

// ── Cancellable handle ─────────────────────────────────────────────────────

/** Returned by [FormField.watch] methods. Call [cancel] to stop observing. */
class ComposureSubscription(private val job: Job) {
    fun cancel() = job.cancel()
}

// ── FormField — the only Kotlin type Swift code needs to touch ─────────────

/**
 * A type-erased wrapper around [FieldState] that captures its own [CoroutineScope].
 * Swift never needs to know about generics, StateFlow, or coroutines.
 *
 * Created by [ComposureFormScope.emailField], [passwordField], [confirmField].
 */
class FormField internal constructor(
    internal val state: FieldState<*>,
    private val scope: CoroutineScope,
) {
    // ── Reactive observers — call these inside FieldViewModel.init ──────────

    fun watchValue(onChange: (String) -> Unit): ComposureSubscription =
        ComposureSubscription(scope.launch { state.value.collect { onChange(it) } })

    fun watchError(onChange: (String?) -> Unit): ComposureSubscription =
        ComposureSubscription(scope.launch { state.error.collect { onChange(it) } })

    fun watchIsTouched(onChange: (Boolean) -> Unit): ComposureSubscription =
        ComposureSubscription(scope.launch { state.isTouched.collect { onChange(it) } })

    fun watchIsValidating(onChange: (Boolean) -> Unit): ComposureSubscription =
        ComposureSubscription(scope.launch { state.isValidating.collect { onChange(it) } })

    fun watchIsDirty(onChange: (Boolean) -> Unit): ComposureSubscription =
        ComposureSubscription(scope.launch { state.isDirty.collect { onChange(it) } })

    // ── Interactions ────────────────────────────────────────────────────────

    fun update(value: String) = state.onChange(value)
    fun blur() = state.onBlur()
    fun reset() = state.reset()
}

// ── ComposureFormScope — the single Kotlin object Swift ComposureForm wraps ─

/**
 * Bundles a [FormScope] with its [CoroutineScope] so Swift never has to
 * create or pass a coroutine scope manually.
 *
 * ```swift
 * // Swift — completely hidden inside ComposureForm base class
 * let composureScope = ComposureFormScope()
 * ```
 */
class ComposureFormScope {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val formScope: FormScope = FormScope(scope)

    // ── Form-level reactive observers ───────────────────────────────────────

    fun watchIsValid(onChange: (Boolean) -> Unit): ComposureSubscription =
        ComposureSubscription(scope.launch { formScope.isValid.collect { onChange(it) } })

    fun watchIsSubmitting(onChange: (Boolean) -> Unit): ComposureSubscription =
        ComposureSubscription(scope.launch { formScope.isSubmitting.collect { onChange(it) } })

    fun watchSubmitError(onChange: (String?) -> Unit): ComposureSubscription =
        ComposureSubscription(scope.launch { formScope.submitError.collect { onChange(it) } })

    fun watchIsDirty(onChange: (Boolean) -> Unit): ComposureSubscription =
        ComposureSubscription(scope.launch { formScope.isDirty.collect { onChange(it) } })

    // ── Submit ──────────────────────────────────────────────────────────────

    /** Validates and submits the form. Calls [onSuccess] if all fields pass. */
    fun submit(onSuccess: () -> Unit) {
        val action = formScope.handleSubmit { _ -> onSuccess() }
        action()
    }

    fun reset() = formScope.reset()

    // ── Field factories ─────────────────────────────────────────────────────

    /**
     * Registers an email field (required + email format built in).
     * Pass an [asyncValidator] for server-side checks like uniqueness.
     */
    fun emailField(
        asyncValidator:  AsyncFieldValidator? = null,
        optional:        Boolean = false,
        requiredMessage: String? = null,
        formatMessage:   String? = null,
    ): FormField = FormField(formScope.field(Email) {
        if (optional) optional()
        if (requiredMessage != null || formatMessage != null) messages(
            required = requiredMessage ?: "This field is required",
            format   = formatMessage   ?: "Enter a valid email address",
        )
        if (asyncValidator != null) async(asyncValidator)
    }, scope)

    /**
     * Registers a password field with configurable strength rules.
     * Defaults: minLength=8, requireUppercase=true, requireDigit=true.
     */
    fun passwordField(
        minLength:       Int     = 8,
        requireUppercase: Boolean = true,
        requireDigit:    Boolean = true,
        requiredMessage: String? = null,
    ): FormField = FormField(formScope.field(Password) {
        if (requiredMessage != null) messages(required = requiredMessage)
        if (minLength > 0) minLength(minLength)
        if (requireUppercase) hasUppercase()
        if (requireDigit) hasDigit()
    }, scope)

    /** Registers a plain text field for "Confirm password" — no validators, masked by ComposureField(isSecure:). */
    fun confirmField(): FormField =
        FormField(formScope.field(Text), scope)

    /**
     * Registers a "Confirm password" field that validates against [matching] using [mustMatch].
     * The error re-triggers automatically whenever [matching] changes — no manual wiring needed.
     */
    @Suppress("UNCHECKED_CAST")
    fun confirmField(matching: FormField): FormField =
        FormField(
            formScope.field(Password) {
                mustMatch(matching.state as FieldState<Password>)
            },
            scope,
        )

    // ── Async validator bridge ──────────────────────────────────────────────

    /**
     * Wraps a callback-based async check into an [AsyncFieldValidator].
     * The callback receives the field value and should call [onResult] with:
     * - `null` → valid
     * - a non-null string → the error message
     *
     * ```swift
     * composureScope.asyncValidator { value, callback in
     *     Task {
     *         let error = await checkEmailAvailability(value)
     *         callback(error)
     *     }
     * }
     * ```
     */
    /**
     * Bridges a Swift async check into a Kotlin [AsyncFieldValidator].
     *
     * The [check] lambda receives:
     * - `value` — the current field text
     * - `callback` — call with `nil` for valid, or an error string for invalid
     *
     * Uses [CompletableDeferred] to suspend the coroutine until [callback] fires,
     * so the main thread is never blocked.
     *
     * ```swift
     * composureScope.asyncValidator { value, callback in
     *     Task {
     *         let isAvailable = await api.checkEmail(value)
     *         callback(isAvailable ? nil : "Already registered")
     *     }
     * }
     * ```
     */
    fun asyncValidator(check: (String, AsyncCheckCallback) -> Unit): AsyncFieldValidator =
        AsyncFieldValidator { value ->
            val deferred = CompletableDeferred<ValidationResult>()
            check(value, AsyncCheckCallback { errorMessage ->
                deferred.complete(
                    if (errorMessage == null) ValidationResult.Valid
                    else ValidationResult.Invalid(errorMessage)
                )
            })
            deferred.await()
        }
}
