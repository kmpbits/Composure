# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-08-24

Initial release.

### Added

- `composure-core` — the validation engine: typed fields (`Email`, `Password`, `Name`, `Phone`, `Text`), sync and async validators, dependent-field validation (`mustMatch`/`dependsOn`), and `StateFlow`-based observable field/form state.
- `composure-compose` — `rememberFormState` for wiring a `FormScope` into Compose Multiplatform's lifecycle, supporting both inline named-field forms and typed form classes.
- `composure-ios` — a Swift-friendly bridge (`ComposureFormScope`, `FormField`) exposing the same engine to SwiftUI without leaking coroutines or generics across the interop boundary.
- Test coverage for `rememberFormState` and Compose state binding (`composure-compose`), and for the Swift bridge layer (`composure-ios`).
- CI (`ci.yml`) running Gradle tests across all targets, plus a build of the iOS sample app against a freshly assembled XCFramework, on every push and pull request.
- Release pipeline (`release.yml`) triggered by pushing a `vX.Y.Z` tag: publishes `composure-core`, `composure-compose`, and `composure-ios` to Maven Central, and publishes the `composure-ios` XCFramework as a GitHub Release and Swift Package.
- README with install and usage instructions for both Compose Multiplatform and SwiftUI.
- Sample apps (`sample/composeApp`, `iosApp/`) demonstrating both the inline and typed-class form patterns.
- Apache 2.0 license.

[Unreleased]: https://github.com/kmpbits/Composure/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/kmpbits/Composure/releases/tag/v0.1.0
