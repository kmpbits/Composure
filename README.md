# Composure

[![CI](https://github.com/kmpbits/Composure/actions/workflows/ci.yml/badge.svg)](https://github.com/kmpbits/Composure/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kmpbits/composure-core?label=release)](https://central.sonatype.com/artifact/io.github.kmpbits/composure-core)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Type-safe, coroutine-based form state and validation for Kotlin Multiplatform — one form definition shared across Compose Multiplatform and SwiftUI.

- **composure-core** — the validation engine: typed fields, sync/async validators, `StateFlow`-based observable state. Pure Kotlin, no UI framework dependency.
- **composure-compose** — `rememberFormState`, wiring a `FormScope` into Compose's lifecycle.
- **composure-ios** — a Swift-friendly bridge (`ComposureFormScope`, `FormField`) that exposes the same engine to SwiftUI without leaking coroutines or generics across the interop boundary.

## Install

**Compose Multiplatform** (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("io.github.kmpbits:composure-core:<version>")
    implementation("io.github.kmpbits:composure-compose:<version>")
}
```

**SwiftUI** — via Swift Package Manager (recommended):

1. In Xcode, go to *File → Add Package Dependencies…*
2. Paste the repository URL into the search field in the top-right corner:
   ```
   https://github.com/kmpbits/Composure.git
   ```
3. Under *Dependency Rule*, pick a version rule (e.g. "Up to Next Major Version" starting at `0.2.0`), then click **Add Package**.
4. In the target-selection sheet that appears, check the `Composure` product against the app target(s) that need it, then click **Add Package** again.

Or, if you're depending on it from your own Swift package instead of an Xcode project, add it to `Package.swift`:

```swift
.package(url: "https://github.com/kmpbits/Composure.git", from: "0.2.0")
```

and add `.product(name: "Composure", package: "Composure")` to the relevant target's dependencies.

Either way, `import composure_ios` in your Swift files afterward. Releases are published automatically from CI whenever a `vX.Y.Z` tag is pushed — see `.github/workflows/release.yml`.

Alternatively, build the XCFramework yourself and link it manually:

```bash
./gradlew :composure-ios:assembleComposureIosReleaseXCFramework
```

This produces `composure-ios/build/XCFrameworks/release/composureIos.xcframework`. Add it to your Xcode project's *Frameworks, Libraries, and Embedded Content*. See `iosApp/` in this repo for a complete sample, including the `ComposureForm`/`ComposureField` SwiftUI wrapper pattern in `iosApp/iosApp/ComposureFormKit.swift`.

## Usage (Compose Multiplatform)

Declare fields inline and read them back by name:

```kotlin
val form = rememberFormState {
    field("email", Email)
    field("password", Password) { minLength(8) }
}

val email by form.get<Email>("email").value.collectAsState()
val isValid by form.isValid.collectAsState()

OutlinedTextField(
    value = email,
    onValueChange = { form.get<Email>("email").onChange(it) },
    label = { Text("Email") },
)

Button(onClick = form.handleSubmit { values -> /* submit */ }, enabled = isValid) {
    Text("Sign in")
}
```

Or declare a typed form class for compile-time-safe field access:

```kotlin
class RegistrationForm(scope: FormScope) : FormController by scope {
    val email = scope.field(Email) { async(checkEmailAvailability) }
    val password = scope.field(Password) { minLength(8); hasUppercase(); hasDigit() }
    val confirm = scope.field(Password) { mustMatch(password) }
}

val form = rememberFormState { scope -> RegistrationForm(scope) }
```

Built-in field types: `Email`, `Password`, `Name`, `Phone`, `Text` — each pre-bakes sensible default validators (e.g. `Email` requires a value and a valid format). Implement `FieldType` yourself for custom types.

### Custom error messages

Every built-in validator takes a `message` parameter, and `messages()` overrides a type's default required/format messages:

```kotlin
val email = scope.field(Email) {
    messages(required = "We need your email", format = "That doesn't look like an email")
}

val password = scope.field(Password) {
    minLength(8, "Needs to be at least 8 characters")
}
```

For fully custom validation logic, use `addValidator` inside the field's builder block:

```kotlin
val username = scope.field(Text) {
    addValidator { value ->
        if (value.contains(" ")) ValidationResult.Invalid("No spaces allowed")
        else ValidationResult.Valid
    }
}
```

## Usage (SwiftUI)

```swift
class RegistrationForm: ComposureForm {
    lazy var email = makeEmailField()
    lazy var password = makePasswordField()
    lazy var confirm = makeConfirmField(matching: password)
}
```

`ComposureForm` and `ComposureField` (in `iosApp/iosApp/ComposureFormKit.swift`) wrap `ComposureFormScope`/`FormField` from `composure-ios` into `ObservableObject`/SwiftUI `View` types, so field state updates drive `@Published` properties without any manual coroutine bridging.

## Development

```bash
./gradlew allTests                                    # run tests across all modules and targets
./gradlew :composure-ios:assembleComposureIosDebugXCFramework  # rebuild the XCFramework iosApp links against
```

`sample/composeApp` and `iosApp/` are runnable reference apps demonstrating both the inline and typed-class patterns above.

## License

Apache 2.0 — see [LICENSE](LICENSE).
