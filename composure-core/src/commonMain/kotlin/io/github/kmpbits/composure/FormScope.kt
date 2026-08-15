package io.github.kmpbits.composure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The form-building scope and runtime controller.
 *
 * Passed into your form class constructor via [rememberFormState]. Call
 * [field] inside the constructor to declare typed fields; delegate
 * [FormController] to expose form-level state to the UI.
 *
 * All observable state is exposed as [StateFlow], making [FormScope]
 * usable from both Compose (`collectAsState()`) and SwiftUI (KMP coroutine interop).
 *
 * ```kotlin
 * class LoginForm(scope: FormScope) : FormController by scope {
 *     val email    = scope.field(Email)
 *     val password = scope.field(Password) { minLength(8) }
 * }
 * ```
 */
class FormScope(
    coroutineScope: CoroutineScope,
    val asyncDebounceMs: Long = 300L,
) : FormController {

    // Mutable so composure-compose can rebind the live CoroutineScope after
    // recomposition without recreating the entire FormScope.
    private var _coroutineScope: CoroutineScope = coroutineScope

    /** Updates the active [CoroutineScope]. Called by [rememberFormState] via SideEffect. */
    fun bindCoroutineScope(scope: CoroutineScope) {
        _coroutineScope = scope
    }

    // ---------------------------------------------------------------------------
    // Internal bookkeeping
    // ---------------------------------------------------------------------------

    private data class FieldEntry(
        val state: FieldState<*>,
        val validators: List<FieldValidator>,
        val asyncValidator: AsyncFieldValidator?,
        val trigger: ValidationTrigger,
        val initialValue: String,
        val isOptional: Boolean = false,
    )

    private val _entries = mutableListOf<FieldEntry>()
    private val _namedFields = mutableMapOf<String, FieldState<*>>()
    private val _stableIds = mutableMapOf<String, String>() // fieldName -> user-provided name
    private val _dependents =
        mutableMapOf<String, MutableList<String>>() // fieldName -> fields that depend on it
    private val _asyncJobs = mutableMapOf<String, Job>()
    private var _fieldCounter = 0

    // ---------------------------------------------------------------------------
    // FormController — all StateFlows
    // ---------------------------------------------------------------------------

    private val _isValid = MutableStateFlow(false)
    private val _isDirty = MutableStateFlow(false)
    private val _isSubmitting = MutableStateFlow(false)
    private val _submitError = MutableStateFlow<String?>(null)

    override val isValid: StateFlow<Boolean> = _isValid.asStateFlow()
    override val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()
    override val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()
    override val submitError: StateFlow<String?> = _submitError.asStateFlow()

    override fun handleSubmit(
        onValid: suspend (values: Map<String, String>) -> Unit,
    ): () -> Unit = {
        _coroutineScope.launch {
            touchAll()
            validateAll()
            updateFormState() // recalculate after forced validation
            if (!_isValid.value) return@launch

            _submitError.value = null
            _isSubmitting.value = true
            try {
                onValid(currentValues())
            } catch (e: Exception) {
                _submitError.value = e.message
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    override fun reset() {
        _entries.forEach { entry ->
            entry.state._value.value = entry.initialValue
            entry.state._error.value = null
            entry.state._isTouched.value = false
            entry.state._isDirty.value = false
            entry.state._isValidating.value = false
        }
        _asyncJobs.values.forEach { it.cancel() }
        _asyncJobs.clear()
        _submitError.value = null
        updateFormState()
    }

    // ---------------------------------------------------------------------------
    // Field registration
    // ---------------------------------------------------------------------------

    /**
     * Declares a typed field and returns its observable [FieldState].
     *
     * ```kotlin
     * val email    = scope.field(Email) { async(checkEmailUseCase) }
     * val password = scope.field(Password) { minLength(8); hasDigit() }
     * val username = scope.field(Username) { noSpaces() } // custom type
     * ```
     */
    fun <T : FieldType> field(
        type: T,
        block: FieldBuilder<T>.() -> Unit = {},
    ): FieldState<T> {
        val builder = FieldBuilder(type).apply(block)
        val name = "field_${_fieldCounter++}"

        val state = FieldState(
            type = type,
            fieldName = name,
            initialValue = builder.initialValue,
        )
        state._scope = this

        for (dep in builder.dependentValidators) {
            _dependents.getOrPut(dep.dependency.fieldName) { mutableListOf() }.add(name)
        }

        val defaultValidators = builder.overrideDefaultValidators.ifEmpty { type.defaultValidators }

        _entries += FieldEntry(
            state = state,
            validators = defaultValidators + builder.validators + builder.dependentValidators,
            asyncValidator = builder.asyncValidator,
            trigger = builder.trigger,
            initialValue = builder.initialValue,
            isOptional = builder.isOptional,
        )
        return state
    }

    /**
     * Declares a **named** typed field for the inline [rememberFormState] API.
     * Access it later with `form["name"]`.
     *
     * ```kotlin
     * val form = rememberFormState {
     *     field("email", Email) { async(checkEmailUseCase) }
     *     field("password", Password) { minLength(8) }
     * }
     * ComposureTextField(field = form["email"], label = "Email")
     * ```
     */
    fun <T : FieldType> field(
        name: String,
        type: T,
        block: FieldBuilder<T>.() -> Unit = {},
    ): FieldState<T> {
        val state = field(type, block)
        _namedFields[name] = state
        _stableIds[state.fieldName] = name
        return state
    }

    /** Retrieves a named field. Throws if the name was never registered. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T : FieldType> get(name: String): FieldState<T> =
        (_namedFields[name] as? FieldState<T>)
            ?: error("No field named '$name'. Did you register it with field(\"$name\", …)?")

    // ---------------------------------------------------------------------------
    // Interaction handlers — called by FieldState.onChange / onBlur
    // ---------------------------------------------------------------------------

    internal fun onFieldChange(state: FieldState<*>, newValue: String) {
        state._value.value = newValue
        state._isDirty.value = true

        val entry = entryFor(state) ?: return

        val syncPassed = if (entry.trigger == ValidationTrigger.ON_CHANGE) {
            runSyncValidation(state, entry)
        } else true

        if (entry.asyncValidator != null) {
            val skipAsync = !syncPassed || (entry.isOptional && state._value.value.isBlank())
            if (!skipAsync) {
                scheduleAsyncValidation(state, entry)
            } else {
                _asyncJobs[state.fieldName]?.cancel()
                _asyncJobs.remove(state.fieldName)
                state._isValidating.value = false
            }
        }

        // Re-validate touched fields that declared a dependency on this one
        _dependents[state.fieldName]?.forEach { dependentName ->
            _entries.find { it.state.fieldName == dependentName }?.let { dep ->
                if (dep.state._isTouched.value) runSyncValidation(dep.state, dep)
            }
        }

        updateFormState()
    }

    internal fun onFieldBlur(state: FieldState<*>) {
        state._isTouched.value = true
        val entry = entryFor(state) ?: return
        if (entry.trigger == ValidationTrigger.ON_BLUR) {
            runSyncValidation(state, entry)
            updateFormState()
        }
    }

    // ---------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------

    /** Returns true if all sync validators passed. */
    private fun runSyncValidation(state: FieldState<*>, entry: FieldEntry): Boolean {
        if (entry.isOptional && state._value.value.isBlank()) {
            state._error.value = null
            return true
        }
        for (validator in entry.validators) {
            val result = validator.validate(state._value.value)
            if (result is ValidationResult.Invalid) {
                state._error.value = result.message
                return false
            }
        }
        if (entry.asyncValidator == null || !state._isValidating.value) {
            state._error.value = null
        }
        return true
    }

    private fun scheduleAsyncValidation(state: FieldState<*>, entry: FieldEntry) {
        val asyncValidator = entry.asyncValidator ?: return

        _asyncJobs[state.fieldName]?.cancel()
        state._isValidating.value = true
        updateFormState()

        _asyncJobs[state.fieldName] = _coroutineScope.launch {
            delay(asyncDebounceMs)
            val result = asyncValidator.validate(state._value.value)
            state._isValidating.value = false
            state._error.value = when (result) {
                is ValidationResult.Valid -> null
                is ValidationResult.Invalid -> result.message
            }
            updateFormState()
        }
    }

    private fun touchAll() = _entries.forEach { it.state._isTouched.value = true }
    private fun validateAll() = _entries.forEach { runSyncValidation(it.state, it) }
    private fun currentValues() = _entries.associate { it.state.fieldName to it.state._value.value }
    private fun entryFor(state: FieldState<*>) =
        _entries.find { it.state.fieldName == state.fieldName }

    /** Resets a single field to its initial state. Called by [FieldState.reset]. */
    internal fun resetField(state: FieldState<*>) {
        val entry = entryFor(state) ?: return
        state._value.value = entry.initialValue
        state._error.value = null
        state._isTouched.value = false
        state._isDirty.value = false
        state._isValidating.value = false
        _asyncJobs[state.fieldName]?.cancel()
        _asyncJobs.remove(state.fieldName)
        updateFormState()
    }

    /** Recomputes form-level validity and dirty state from all fields. */
    private fun updateFormState() {
        _isValid.value = _entries.all { it.state.isValid }
        _isDirty.value = _entries.any { it.state._isDirty.value }
    }

    // ---------------------------------------------------------------------------
    // Manual state save/restore (useful for ViewModel + SavedStateHandle)
    // ---------------------------------------------------------------------------

    fun saveFieldData(): Map<String, List<String?>> =
        _entries.associate { entry ->
            val key = _stableIds[entry.state.fieldName] ?: entry.state.fieldName
            key to listOf(
                entry.state._value.value,
                entry.state._error.value,
                entry.state._isTouched.value.toString(),
                entry.state._isDirty.value.toString(),
            )
        }

    fun restoreFieldData(data: Map<String, List<String?>>) {
        _entries.forEach { entry ->
            val key = _stableIds[entry.state.fieldName] ?: entry.state.fieldName
            val saved = data[key] ?: return@forEach
            entry.state._value.value = saved[0] ?: ""
            entry.state._error.value = saved[1]
            entry.state._isTouched.value = saved[2]?.toBoolean() ?: false
            entry.state._isDirty.value = saved[3]?.toBoolean() ?: false
        }
        updateFormState()
    }
}
