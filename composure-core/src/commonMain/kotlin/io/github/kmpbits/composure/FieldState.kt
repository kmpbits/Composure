package io.github.kmpbits.composure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds all observable state for a single form field as [StateFlow]s,
 * making it consumable from both Compose (via `collectAsState()`) and
 * SwiftUI (via KMP's coroutine-Swift interop).
 *
 * [T] is the [FieldType] — carries keyboard hints, masking config,
 * and default validators.
 */
class FieldState<T : FieldType> internal constructor(
    val type: T,
    internal val fieldName: String,
    initialValue: String = "",
    initialError: String? = null,
    initialIsTouched: Boolean = false,
    initialIsDirty: Boolean = false,
) {
    internal lateinit var _scope: FormScope

    // ---------------------------------------------------------------------------
    // Backing flows — internal so FormScope can mutate them directly
    // ---------------------------------------------------------------------------

    internal val _value = MutableStateFlow(initialValue)
    internal val _error = MutableStateFlow(initialError)
    internal val _isTouched = MutableStateFlow(initialIsTouched)
    internal val _isDirty = MutableStateFlow(initialIsDirty)
    internal val _isValidating = MutableStateFlow(false)

    // ---------------------------------------------------------------------------
    // Public read-only StateFlows
    // Compose: val value by field.value.collectAsState()
    // SwiftUI: observe field.value via asyncStream / Combine
    // ---------------------------------------------------------------------------

    /** The current text value. */
    val value: StateFlow<String> = _value.asStateFlow()

    /** The current validation error, or null when valid. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * True once the user has focused and left this field.
     * Use this to gate error display in the UI.
     */
    val isTouched: StateFlow<Boolean> = _isTouched.asStateFlow()

    /** True once the user has changed the value at least once. */
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    /** True while an async validator is running for this field. */
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    // ---------------------------------------------------------------------------
    // Synchronous convenience helpers (read current flow values)
    // ---------------------------------------------------------------------------

    val hasError: Boolean get() = _error.value != null
    val isValid: Boolean get() = _error.value == null && !_isValidating.value

    // ---------------------------------------------------------------------------
    // User interaction — wire these to your text field's callbacks,
    // or let ComposureTextField handle them automatically.
    // ---------------------------------------------------------------------------

    fun onChange(newValue: String) = _scope.onFieldChange(this, newValue)
    fun onBlur() = _scope.onFieldBlur(this)

    /** Resets this field to its initial value and clears all state, without touching other fields. */
    fun reset() = _scope.resetField(this)
}
