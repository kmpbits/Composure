// ComposureFormKit.swift
// Drop this single file into any SwiftUI project that links composure_core.xcframework.
//
// Usage:
//
//   class LoginForm: ComposureForm {
//       lazy var email    = makeEmailField()
//       lazy var password = makePasswordField(minLength: 0)
//   }
//
//   struct LoginView: View {
//       @StateObject var form = LoginForm()
//       var body: some View {
//           ComposureField("Email",    field: form.email)
//           ComposureField("Password", field: form.password, isSecure: true)
//           Button("Sign in") { form.submit { } }.disabled(!form.isValid)
//       }
//   }

import SwiftUI
import composure_ios

// ─────────────────────────────────────────────────────────────────────────────
// MARK: – FieldViewModel
// Wraps a Kotlin FormField. All @Published properties drive the SwiftUI view.
// ─────────────────────────────────────────────────────────────────────────────

@MainActor
public final class FieldViewModel: ObservableObject {

    @Published public private(set) var value = ""
    @Published public private(set) var error: String? = nil
    @Published public private(set) var isTouched = false
    @Published public private(set) var isDirty = false
    @Published public private(set) var isValidating = false

    internal let field: FormField
    private var subscriptions: [ComposureSubscription] = []

    init(field: FormField) {
        self.field = field
        subscriptions = [
            field.watchValue { [weak self] v in self?.value = v },
            field.watchError { [weak self] v in self?.error = v },
            field.watchIsTouched { [weak self] v in self?.isTouched = v.boolValue },
            field.watchIsDirty { [weak self] v in self?.isDirty = v.boolValue },
            field.watchIsValidating { [weak self] v in self?.isValidating = v.boolValue },
        ]
    }

    public func onChange(_ newValue: String) { field.update(value: newValue) }
    public func onBlur() { field.blur() }
    public func reset() { field.reset() }

    deinit { subscriptions.forEach { $0.cancel() } }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: – ComposureForm
// Subclass this instead of ObservableObject. Declare fields as lazy vars.
// ─────────────────────────────────────────────────────────────────────────────

@MainActor
open class ComposureForm: ObservableObject {

    @Published public private(set) var isValid = false
    @Published public private(set) var isDirty = false
    @Published public private(set) var isSubmitting = false
    @Published public private(set) var submitError: String? = nil

    private let composureScope = ComposureFormScope()
    private var subscriptions: [ComposureSubscription] = []

    public init() {
        subscriptions = [
            composureScope.watchIsValid { [weak self] v in self?.isValid = v.boolValue },
            composureScope.watchIsDirty { [weak self] v in self?.isDirty = v.boolValue },
            composureScope.watchIsSubmitting { [weak self] v in self?.isSubmitting = v.boolValue },
            composureScope.watchSubmitError { [weak self] v in self?.submitError = v },
        ]
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /// Validates all fields and calls [onSuccess] if the form is valid.
    public func submit(onSuccess: @escaping () -> Void) {
        composureScope.submit(onSuccess: onSuccess)
    }

    /// Resets all fields to their initial state.
    public func reset() { composureScope.reset() }

    // ── Field factories ───────────────────────────────────────────────────────

    /// Email field — required + email-format validators included automatically.
    /// Pass an async closure for server-side checks (e.g. uniqueness).
    ///
    ///     lazy var email = makeEmailField { value, callback in
    ///         Task {
    ///             let taken = await api.isEmailTaken(value)
    ///             callback(taken ? "Already registered" : nil)
    ///         }
    ///     }
    public func makeEmailField(
        optional:        Bool    = false,
        requiredMessage: String? = nil,
        formatMessage:   String? = nil,
        asyncCheck: ((String, @escaping (String?) -> Void) -> Void)? = nil
    ) -> FieldViewModel {
        let validator = asyncCheck.map { userCheck in
            composureScope.asyncValidator { value, callback in
                userCheck(value) { errorMsg in
                    callback.complete(errorMessage: errorMsg)
                }
            }
        }
        return FieldViewModel(field: composureScope.emailField(
            asyncValidator:  validator,
            optional:        optional,
            requiredMessage: requiredMessage,
            formatMessage:   formatMessage
        ))
    }

    /// Password field with configurable strength rules.
    public func makePasswordField(
        minLength:        Int     = 8,
        requireUppercase: Bool    = true,
        requireDigit:     Bool    = true,
        requiredMessage:  String? = nil
    ) -> FieldViewModel {
        FieldViewModel(field: composureScope.passwordField(
            minLength:        Int32(minLength),
            requireUppercase: requireUppercase,
            requireDigit:     requireDigit,
            requiredMessage:  requiredMessage
        ))
    }

    /// Plain password field — use for "Confirm password" inputs (Swift-side validation).
    public func makeConfirmField() -> FieldViewModel {
        FieldViewModel(field: composureScope.confirmField())
    }

    /// Confirm-password field that validates against [matching] automatically.
    /// Errors re-trigger whenever [matching] changes — no manual wiring needed.
    ///
    ///     lazy var password = makePasswordField()
    ///     lazy var confirm  = makeConfirmField(matching: password)
    public func makeConfirmField(matching: FieldViewModel) -> FieldViewModel {
        FieldViewModel(field: composureScope.confirmField(matching: matching.field))
    }

    deinit { subscriptions.forEach { $0.cancel() } }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARK: – ComposureField
// Drop-in SwiftUI text field that wires directly to a FieldViewModel.
// ─────────────────────────────────────────────────────────────────────────────

public struct ComposureField: View {

    public let label: String
    @ObservedObject public var field: FieldViewModel
    public var isSecure: Bool = false

    public init(_ label: String, field: FieldViewModel, isSecure: Bool = false) {
        self.label = label
        self.field = field
        self.isSecure = isSecure
    }

    @FocusState private var focused: Bool

    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ZStack(alignment: .trailing) {
                Group {
                    if isSecure {
                        SecureField(
                            label,
                            text: .init(
                                get: { field.value },
                                set: { field.onChange($0) }
                            ))
                    } else {
                        TextField(
                            label,
                            text: .init(
                                get: { field.value },
                                set: { field.onChange($0) }
                            )
                        )
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    }
                }
                .textFieldStyle(.roundedBorder)
                .focused($focused)
                .onChange(of: focused) { isFocused in
                    if !isFocused { field.onBlur() }
                }
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(borderColor, lineWidth: 1.5)
                )

                if field.isValidating {
                    ProgressView().scaleEffect(0.75).padding(.trailing, 8)
                }
            }

            if let error = field.error, field.isDirty || field.isTouched {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .padding(.leading, 4)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.15), value: field.error)
    }

    private var borderColor: Color {
        if field.error != nil && (field.isDirty || field.isTouched) { return .red }
        if focused { return .accentColor }
        return Color(.systemGray4)
    }
}
