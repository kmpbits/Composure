package io.github.kmpbits.composure

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Creates and remembers a [FormScope]-backed form for the lifetime of the
 * current composition.
 *
 * Pass a [factory] lambda that receives a [FormScope] and returns your own
 * form class. Fields declared via [FormScope.field] are automatically
 * registered and managed.
 *
 * **Does not survive configuration changes.** This behaves like plain
 * `remember` — the form is lost whenever composition is discarded (e.g. an
 * Android configuration change). There is intentionally no `rememberSaveable`
 * variant: [FormScope] holds a `CoroutineScope`, `StateFlow`s, and `Job`s,
 * none of which are `Bundle`-safe, and "configuration change" isn't even a
 * concept on iOS/Desktop/Wasm.
 *
 * If a form needs to survive configuration changes, hoist it into a
 * `ViewModel` instead. [FormScope] is plain Kotlin, so it fits naturally in a
 * ViewModel without any Compose dependency:
 *
 * ```kotlin
 * class LoginViewModel : ViewModel() {
 *     private val scope = FormScope(viewModelScope)
 *     val form = LoginForm(scope)
 * }
 * ```
 *
 * For process-death survival on top of that, use [FormScope.saveFieldData] /
 * [FormScope.restoreFieldData] to round-trip field values through a
 * `SavedStateHandle` — see the README's "Surviving configuration changes"
 * section for a full example.
 *
 * ## Example
 *
 * ```kotlin
 * class RegistrationForm(scope: FormScope) : FormController by scope {
 *     val email = scope.field(Email) { async(checkEmailUseCase) }
 *     val password = scope.field(Password) { minLength(8); hasDigit() }
 *     val confirm = scope.field(Password)
 * }
 *
 * val form = rememberFormState { scope -> RegistrationForm(scope) }
 *
 * val isValid by form.isValid.collectAsState()
 * Button(
 *     onClick = form.handleSubmit { values -> viewModel.register(values) },
 *     enabled = isValid,
 * )
 * ```
 *
 * @param asyncDebounceMs Milliseconds to wait after the last keystroke before
 *   firing async validators. Defaults to 300ms.
 * @param factory Lambda that receives [FormScope] and returns your form object.
 */
@Composable
fun <F> rememberFormState(
    asyncDebounceMs: Long = 300L,
    factory: (FormScope) -> F,
): F {
    val coroutineScope = rememberCoroutineScope()
    // Pair<FormScope, F> stored together so factory is only called once.
    val holder = remember {
        val scope = FormScope(coroutineScope, asyncDebounceMs)
        scope to factory(scope)
    }
    // Keep the coroutine scope current across recompositions.
    SideEffect { holder.first.bindCoroutineScope(coroutineScope) }
    return holder.second
}

/**
 * Inline variant of [rememberFormState] — no form class required.
 *
 * Declare fields directly inside the block using [FormScope.field] with a
 * name key. Retrieve them later via `form["name"]`. The returned [FormScope]
 * also implements [FormController], so `form.handleSubmit`, etc. are available
 * directly.
 *
 * ## Example
 *
 * ```kotlin
 * val form = rememberFormState {
 *     field("email", Email) { async(checkEmailUseCase) }
 *     field("password", Password) { minLength(8) }
 * }
 *
 * val isValid by form.isValid.collectAsState()
 * ComposureTextField(field = form["email"], label = "Email")
 *
 * Button(
 *     onClick = form.handleSubmit { values -> viewModel.login(values) },
 *     enabled = isValid,
 * )
 * ```
 *
 * @param asyncDebounceMs Milliseconds to wait after the last keystroke before
 *   firing async validators. Defaults to 300ms.
 * @param block Receiver lambda on [FormScope] — declare your fields here.
 */
@Composable
fun rememberFormState(
    asyncDebounceMs: Long = 300L,
    block: FormScope.() -> Unit,
): FormScope {
    val coroutineScope = rememberCoroutineScope()
    val scope = remember {
        FormScope(coroutineScope, asyncDebounceMs).also { it.block() }
    }
    SideEffect { scope.bindCoroutineScope(coroutineScope) }
    return scope
}
