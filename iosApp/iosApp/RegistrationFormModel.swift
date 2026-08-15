import Foundation

// Mirrors the "class-based" RegistrationForm from the Compose sample.
// The async validator is the only interesting part — everything else is one line.

class RegistrationForm: ComposureForm {

    lazy var email = makeEmailField(
        requiredMessage: "Email is required",
        formatMessage:   "Enter a valid email address"
    ) { value, callback in
        // Simulates a network call — mirrors the Kotlin AsyncFieldValidator
        Task {
            try? await Task.sleep(nanoseconds: 800_000_000)
            callback(value.lowercased() == "taken@example.com" ? "Already registered" : nil)
        }
    }

    lazy var password = makePasswordField(requiredMessage: "Password is required")
    lazy var confirm  = makeConfirmField(matching: password)  // auto-validates against password
}
