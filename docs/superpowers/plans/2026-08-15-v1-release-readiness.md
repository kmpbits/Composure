# Composure v1 Release Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Close the remaining v1 gaps for the Composure KMP form library: test coverage for `composure-compose` and `composure-ios`, a GitHub Actions CI workflow that would catch a stale-XCFramework-style regression, and a root README.

**Architecture:** Each module gets tests using the lightest dependency that reaches real behavior without flaky async timing: `composure-compose` uses a Desktop JUnit4 Compose test (`createComposeRule`) since it's the only target with a `jvm()` host; `composure-ios` uses plain `kotlin.test` in an `iosTest` source set, asserting through synchronous state (validators run inline on `onChange`) rather than through the `Dispatchers.Main`-backed watch callbacks, which cannot be reliably pumped in a bare Kotlin/Native test binary. CI runs on `macos-14` (required — Kotlin/Native iOS compilation only works on macOS) and, after running all Gradle tests, rebuilds the iOS sample app against a freshly assembled XCFramework so a Kotlin/Swift API drift fails the build instead of shipping silently.

**Tech Stack:** Kotlin 2.1.20, Compose Multiplatform 1.8.0, AGP 8.7.3, kotlinx.coroutines 1.10.1, GitHub Actions.

## Global Constraints

- GROUP = `io.github.kmpbits`, VERSION_NAME = `0.1.0` (from `gradle.properties`) — use these in README Maven coordinates.
- Kotlin `2.1.20`, Compose Multiplatform `1.8.0`, AGP `8.7.3`, coroutines `1.10.1` — from `gradle/libs.versions.toml`; new catalog entries must use `version.ref = "coroutines"` where applicable, not a hardcoded version.
- Do not modify `composure-core`, `composure-compose`'s `Composure.kt`, or `composure-ios`'s `IOSHelpers.kt` production code — this plan is test/docs/CI only.
- Git commits in this repo must NOT include a Claude/Anthropic co-author trailer.

---

### Task 1: composure-compose — `rememberFormState` test coverage

**Files:**
- Modify: `composure-compose/build.gradle.kts`
- Create: `composure-compose/src/desktopTest/kotlin/io/github/kmpbits/composure/ComposureTest.kt`

**Interfaces:**
- Consumes: `rememberFormState` (block and factory overloads) and `FormScope`/`FormController` from `composure-core`, both already public — no new interfaces needed.
- Produces: nothing consumed by later tasks.

- [x] **Step 1: Add the Desktop Compose UI test dependencies**

Edit `composure-compose/build.gradle.kts`. Add the import and the `desktopTest` source set block:

```kotlin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            api(project(":composure-core"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val desktopTest by getting {
            dependencies {
                @OptIn(ExperimentalComposeLibrary::class)
                implementation(compose.desktop.uiTestJUnit4)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
```

(Only the `import` line and the `sourceSets { ... val desktopTest by getting { ... } }` block are new; everything else is unchanged from the current file.)

- [x] **Step 2: Write the failing tests**

Create `composure-compose/src/desktopTest/kotlin/io/github/kmpbits/composure/ComposureTest.kt`:

```kotlin
package io.github.kmpbits.composure

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `block variant returns a working FormScope with the declared named field`() {
        lateinit var form: FormScope

        composeTestRule.setContent {
            form = rememberFormState {
                field("email", Email)
            }
        }

        assertEquals("", form["email"].value.value)
        assertFalse(form.isValid.value)
    }

    @Test
    fun `factory variant returns the object built by the factory, wired to a live FormScope`() {
        class LoginForm(scope: FormScope) : FormController by scope {
            val email = scope.field(Email)
        }

        lateinit var form: LoginForm

        composeTestRule.setContent {
            form = rememberFormState { scope -> LoginForm(scope) }
        }

        assertEquals("", form.email.value.value)
        assertFalse(form.isValid.value)
    }

    @Test
    fun `a field obtained from rememberFormState routes onChange back into form isValid`() {
        lateinit var form: FormScope

        composeTestRule.setContent {
            form = rememberFormState {
                field("email", Email)
            }
        }

        composeTestRule.runOnIdle {
            form["email"].onChange("joel@kmpbits.io")
        }
        composeTestRule.waitForIdle()

        assertTrue(form.isValid.value)
    }

    @Test
    fun `rememberFormState keeps the same FormScope instance across recomposition`() {
        val recomposeTrigger = mutableStateOf(0)
        val capturedForms = mutableListOf<FormScope>()

        composeTestRule.setContent {
            recomposeTrigger.value
            val form = rememberFormState { field("email", Email) }
            capturedForms += form
        }

        composeTestRule.runOnIdle { recomposeTrigger.value = 1 }
        composeTestRule.waitForIdle()

        assertEquals(2, capturedForms.size)
        assertTrue(capturedForms[0] === capturedForms[1])
    }
}
```

- [x] **Step 3: Run the tests to verify they fail for the right reason (missing dependency), then pass**

Run:
```bash
./gradlew :composure-compose:desktopTest
```
Expected on first run (before Step 1's dependency exists, or right after adding the test file if Step 1 was skipped): a compile error like `Unresolved reference: junit` or `Unresolved reference: createComposeRule` — confirming the test file requires the dependency added in Step 1.

After Step 1's `build.gradle.kts` edit is in place, re-run the same command.
Expected: `BUILD SUCCESSFUL`, with 4 tests passing under `composure-compose/build/test-results/desktopTest/`.

If a test fails, read the actual assertion failure — do not change the test's expected values to match wrong behavior. If the *build* fails with an unresolved reference on `compose.desktop.uiTestJUnit4` or `compose.desktop.currentOs`, check the resolved version of `org.jetbrains.compose:compose-gradle-plugin` (`1.8.0` per `gradle/libs.versions.toml`) exposes those accessors under `org.jetbrains.compose.ComposePlugin.Dependencies.Desktop` — they do as of 1.8.0.

- [x] **Step 4: Commit**

```bash
git add composure-compose/build.gradle.kts composure-compose/src/desktopTest
git commit -m "test: add rememberFormState coverage for composure-compose"
```

---

### Task 2: composure-ios — Swift bridge layer test coverage

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composure-ios/build.gradle.kts`
- Create: `composure-ios/src/iosTest/kotlin/io/github/kmpbits/composure/ComposureFormScopeTest.kt`

**Interfaces:**
- Consumes: `ComposureFormScope`, `FormField` (both from `composure-ios`'s `IOSHelpers.kt`), and `ValidationResult` from `composure-core`.
- Produces: nothing consumed by later tasks.

**Design note (why these tests, not `watchValue`/`submit`):** `ComposureFormScope` owns a `CoroutineScope(SupervisorJob() + Dispatchers.Main)`. Anything dispatched through it (`watchValue`, `watchError`, `submit`'s `handleSubmit` launch) only completes if something pumps the platform's main run loop, which a bare Kotlin/Native `iosSimulatorArm64Test` binary does not reliably do — a test built on that would risk hanging CI rather than failing fast. Every field mutation's *validation*, however, runs synchronously inside `onChange`/`onBlur` (see `FormScope.onFieldChange` in `composure-core`), and `FormField.state` is `internal`, which the `iosTest` source set can see as a friend compilation of `iosMain`. So these tests drive fields via `update()`/`blur()`/`reset()` and assert directly on `field.state.value`/`.error`/`.isTouched`, and test `asyncValidator` by invoking the returned `AsyncFieldValidator.validate(...)` directly inside `runTest` — never through the Main-dispatched watch/submit paths.

- [x] **Step 1: Add a `coroutines-test` catalog entry**

Edit `gradle/libs.versions.toml`. In the `[libraries]` section, add a line right after `coroutines-core`:

```toml
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
```

- [x] **Step 2: Add the iosTest source set dependencies**

Edit `composure-ios/build.gradle.kts` — add an `iosTest.dependencies` block inside `sourceSets`:

```kotlin
sourceSets {
    commonMain.dependencies {
        // Exposes all composure-core types transitively to Swift
        api(project(":composure-core"))
    }

    iosTest.dependencies {
        implementation(kotlin("test"))
        implementation(libs.coroutines.test)
    }
}
```

- [x] **Step 3: Write the failing tests**

Create `composure-ios/src/iosTest/kotlin/io/github/kmpbits/composure/ComposureFormScopeTest.kt`:

```kotlin
package io.github.kmpbits.composure

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposureFormScopeTest {

    @Test
    fun `emailField applies required and email format validators by default`() {
        val form = ComposureFormScope()
        val email = form.emailField()

        email.update("")
        assertEquals("This field is required", email.state.error.value)

        email.update("notanemail")
        assertEquals("Enter a valid email address", email.state.error.value)

        email.update("joel@kmpbits.io")
        assertNull(email.state.error.value)
    }

    @Test
    fun `emailField honors custom required and format messages`() {
        val form = ComposureFormScope()
        val email = form.emailField(requiredMessage = "Required!", formatMessage = "Bad format!")

        email.update("")
        assertEquals("Required!", email.state.error.value)

        email.update("notanemail")
        assertEquals("Bad format!", email.state.error.value)
    }

    @Test
    fun `emailField marked optional accepts a blank value`() {
        val form = ComposureFormScope()
        val email = form.emailField(optional = true)

        email.update("")
        assertNull(email.state.error.value)
    }

    @Test
    fun `passwordField enforces default strength rules`() {
        val form = ComposureFormScope()
        val password = form.passwordField()

        password.update("short")
        assertTrue(password.state.error.value != null)

        password.update("longenough1") // 11 chars, has a digit, no uppercase
        assertTrue(password.state.error.value != null)

        password.update("LongEnough1")
        assertNull(password.state.error.value)
    }

    @Test
    fun `passwordField relaxes strength rules when disabled`() {
        val form = ComposureFormScope()
        val password = form.passwordField(minLength = 0, requireUppercase = false, requireDigit = false)

        password.update("a")
        assertNull(password.state.error.value)
    }

    @Test
    fun `confirmField without a matching field has no validators`() {
        val form = ComposureFormScope()
        val confirm = form.confirmField()

        confirm.update("anything")
        assertNull(confirm.state.error.value)
    }

    @Test
    fun `confirmField matching another field validates equality and re-validates live`() {
        val form = ComposureFormScope()
        val password = form.passwordField()
        val confirm = form.confirmField(matching = password)

        password.update("Secret1A")
        confirm.update("different")
        assertEquals("Passwords do not match", confirm.state.error.value)

        confirm.update("Secret1A")
        assertNull(confirm.state.error.value)
    }

    @Test
    fun `update blur and reset delegate to the underlying field`() {
        val form = ComposureFormScope()
        val email = form.emailField()

        email.update("joel@kmpbits.io")
        email.blur()
        assertEquals("joel@kmpbits.io", email.state.value.value)
        assertTrue(email.state.isTouched.value)

        email.reset()
        assertEquals("", email.state.value.value)
        assertFalse(email.state.isTouched.value)
    }

    @Test
    fun `formScope isValid reflects the fields registered through the bridge`() {
        val form = ComposureFormScope()
        val email = form.emailField()

        assertFalse(form.formScope.isValid.value)

        email.update("joel@kmpbits.io")
        assertTrue(form.formScope.isValid.value)
    }

    @Test
    fun `asyncValidator bridges a synchronous valid callback into ValidationResult Valid`() = runTest {
        val form = ComposureFormScope()
        val validator = form.asyncValidator { _, callback -> callback.complete(null) }

        val result = validator.validate("anything")
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `asyncValidator bridges a synchronous invalid callback into ValidationResult Invalid`() = runTest {
        val form = ComposureFormScope()
        val validator = form.asyncValidator { _, callback -> callback.complete("Already taken") }

        val result = validator.validate("anything")
        assertEquals(ValidationResult.Invalid("Already taken"), result)
    }
}
```

- [x] **Step 4: Run the tests to verify they fail for the right reason, then pass**

Run:
```bash
./gradlew :composure-ios:iosSimulatorArm64Test
```
Expected before Steps 1–2 (or if run against only the test file): compile failure — `Unresolved reference: runTest` (missing `coroutines-test` dependency) or `Unresolved reference: state` (if `iosTest` source set isn't wired, so the file isn't even compiled — in that case the task will report `NO-SOURCE`/be silently skipped, which is itself a signal Step 2 wasn't applied).

After Steps 1–2 are in place, re-run the same command.
Expected: `BUILD SUCCESSFUL`, 11 tests passing. This launches an iOS Simulator process to host the Kotlin/Native test binary — that's the Gradle task's normal mechanism for running `iosSimulatorArm64Test`, not something to configure manually.

- [x] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml composure-ios/build.gradle.kts composure-ios/src/iosTest
git commit -m "test: add Swift bridge layer coverage for composure-ios"
```

---

### Task 3: CI — GitHub Actions workflow

**Files:**
- Create: `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme`
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: the `allTests` Gradle lifecycle task (aggregates Task 1 and Task 2's new tests plus the existing `composure-core` tests), and the `:composure-ios:assembleComposureIosDebugXCFramework` task (existing, unmodified).
- Produces: a `ci.yml` workflow whose badge URL Task 4's README references.

**Why a shared scheme is needed:** `iosApp/iosApp.xcodeproj` currently only has a scheme under `xcuserdata/joelcaetano.xcuserdatad/xcschemes/` — a per-developer file that is typically gitignored and, even when present, isn't something `xcodebuild -scheme iosApp` on a fresh CI checkout can rely on. Xcode's `-scheme` flag needs a *shared* scheme, which lives under `xcshareddata/xcschemes/` inside the `.xcodeproj` bundle.

- [x] **Step 1: Check the existing scheme isn't gitignored**

Run:
```bash
git check-ignore -v iosApp/iosApp.xcodeproj/xcuserdata/joelcaetano.xcuserdatad/xcschemes/iosApp.xcscheme
```
If this prints a match (e.g. from a rule like `xcuserdata/`), that confirms the existing scheme is dev-machine-only and never reaches a CI checkout, which is exactly why Step 2 is needed. If it prints nothing (not ignored), the file is already tracked — check `git log --follow -- iosApp/iosApp.xcodeproj/xcuserdata/joelcaetano.xcuserdatad/xcschemes/iosApp.xcscheme` to confirm, but proceed to Step 2 regardless since a shared scheme is still the correct location for CI.

- [x] **Step 2: Create the shared scheme**

```bash
mkdir -p iosApp/iosApp.xcodeproj/xcshareddata/xcschemes
cp iosApp/iosApp.xcodeproj/xcuserdata/joelcaetano.xcuserdatad/xcschemes/iosApp.xcscheme \
   iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme
```

Verify it's readable as a scheme list:
```bash
xcodebuild -list -project iosApp/iosApp.xcodeproj
```
Expected: output includes a `Schemes:` section listing `iosApp` (previously it may have shown no schemes, or only picked up the user scheme depending on the host machine's Xcode state).

- [x] **Step 3: Write the CI workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: macos-14

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run Kotlin tests (composure-core, composure-compose, composure-ios)
        run: ./gradlew allTests --continue

      - name: Assemble the debug XCFramework
        run: ./gradlew :composure-ios:assembleComposureIosDebugXCFramework

      - name: Build the iOS sample app against the freshly assembled framework
        run: |
          xcodebuild build \
            -project iosApp/iosApp.xcodeproj \
            -scheme iosApp \
            -sdk iphonesimulator \
            -destination "generic/platform=iOS Simulator" \
            CODE_SIGNING_ALLOWED=NO
```

- [x] **Step 4: Verify the workflow file is valid YAML and the referenced tasks exist**

Run:
```bash
python3 -c "import yaml, sys; yaml.safe_load(open('.github/workflows/ci.yml'))" && echo "YAML OK"
./gradlew tasks --all | grep -E "^allTests|assembleComposureIosDebugXCFramework"
```
Expected: `YAML OK` printed, and both task names appear in the `gradlew tasks` output (confirming the workflow doesn't reference a task that doesn't exist).

- [x] **Step 5: Run the same commands the workflow runs, locally, to confirm the pipeline actually passes end to end**

```bash
./gradlew allTests --continue
./gradlew :composure-ios:assembleComposureIosDebugXCFramework
xcodebuild build \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  CODE_SIGNING_ALLOWED=NO
```
Expected: all three succeed (`BUILD SUCCESSFUL` for Gradle, `** BUILD SUCCEEDED **` for xcodebuild). If `allTests` reports failures from unrelated pre-existing targets (e.g. `iosArm64Test` with no attached device), confirm via `./gradlew tasks --all` whether that target's test task is enabled at all on this host before treating it as a regression — device-only Apple targets are commonly disabled by the Kotlin Gradle plugin when no device is attached, which is not something this task should try to fix.

- [x] **Step 6: Commit**

```bash
git add iosApp/iosApp.xcodeproj/xcshareddata .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow covering Gradle tests and the iOS sample build"
```

---

### Task 4: README

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: Maven coordinates (`io.github.kmpbits:composure-core:0.1.0` etc. — from Global Constraints), the exact public API surface documented in Task 1/Task 2's research (`rememberFormState`, `FormScope.field`, `FormController`, built-in `FieldType`s), and the CI badge URL from Task 3.
- Produces: nothing consumed by later tasks (this is the last task).

- [x] **Step 1: Write the README**

Create `README.md`:

```markdown
# Composure

[![CI](https://github.com/kmpbits/Composure/actions/workflows/ci.yml/badge.svg)](https://github.com/kmpbits/Composure/actions/workflows/ci.yml)

Type-safe, coroutine-based form state and validation for Kotlin Multiplatform — one form definition shared across Compose Multiplatform and SwiftUI.

- **composure-core** — the validation engine: typed fields, sync/async validators, `StateFlow`-based observable state. Pure Kotlin, no UI framework dependency.
- **composure-compose** — `rememberFormState`, wiring a `FormScope` into Compose's lifecycle.
- **composure-ios** — a Swift-friendly bridge (`ComposureFormScope`, `FormField`) that exposes the same engine to SwiftUI without leaking coroutines or generics across the interop boundary.

## Install

**Compose Multiplatform** (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("io.github.kmpbits:composure-core:0.1.0")
    implementation("io.github.kmpbits:composure-compose:0.1.0")
}
```

**SwiftUI** — build the XCFramework and link it into your Xcode project:

```bash
./gradlew :composure-ios:assembleComposureIosReleaseXCFramework
```

This produces `composure-ios/build/XCFrameworks/release/composureIos.xcframework`. Add it to your Xcode project's *Frameworks, Libraries, and Embedded Content* and `import composure_ios` in Swift. See `iosApp/` in this repo for a complete sample, including the `ComposureForm`/`ComposureField` SwiftUI wrapper pattern in `iosApp/iosApp/ComposureFormKit.swift`.

## Usage (Compose Multiplatform)

Declare fields inline and read them back by name:

```kotlin
val form = rememberFormState {
    field("email", Email)
    field("password", Password) { minLength(8) }
}

val email by form["email"].value.collectAsState()
val isValid by form.isValid.collectAsState()

OutlinedTextField(
    value = email,
    onValueChange = { form["email"].onChange(it) },
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
```

- [x] **Step 2: Verify every code snippet's imports actually resolve against the real API**

Cross-check each identifier used above against its source:
- `rememberFormState`, `field`, `FormScope`, `FormController` — `composure-compose/src/commonMain/kotlin/io/github/kmpbits/composure/Composure.kt`, `composure-core/.../FormScope.kt`, `composure-core/.../FormController.kt`.
- `Email`, `Password`, `Name`, `Phone`, `Text`, `FieldType` — `composure-core/.../FieldType.kt`.
- `minLength`, `hasUppercase`, `hasDigit`, `mustMatch`, `async` — `composure-core/.../FieldBuilder.kt`.
- `makeEmailField`/`makePasswordField`/`makeConfirmField`/`ComposureForm`/`ComposureField` — `iosApp/iosApp/ComposureFormKit.swift`.

This is a read-only cross-check (grep each identifier, confirm it's spelled and used the way the README shows) — no code changes if everything matches, which it does per the Task 1/2 research above.

- [x] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: add README with install and usage instructions"
```

---

## Self-Review Notes

- **Spec coverage:** README (Task 4), composure-compose tests (Task 1), composure-ios tests (Task 2), CI (Task 3) — all four items the user selected are covered. CHANGELOG.md was explicitly not selected and is out of scope.
- **Flakiness avoided on purpose:** Task 2 deliberately does not test `watchValue`/`watchError`/`submit()` end-to-end, because those run through `ComposureFormScope`'s `Dispatchers.Main`-pinned `CoroutineScope`, which a bare Kotlin/Native test binary does not reliably pump — a test relying on it could hang CI rather than fail. Validator wiring (the actual "untested and fragile" concern from the release-readiness review) is still fully covered via the synchronous `state.error`/`state.value` path.
- **iOS-only manual steps** (Central Portal signup, GPG key, publishing) from the prior session are unaffected by this plan and still require the user's own credentials.
