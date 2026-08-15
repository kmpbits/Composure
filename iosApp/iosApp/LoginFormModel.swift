import Foundation

// Mirrors the "inline" approach from the Compose sample —
// no separate class needed, just register fields directly.

class LoginForm: ComposureForm {
    lazy var email = makeEmailField()
    lazy var password = makePasswordField(minLength: 0, requireUppercase: false, requireDigit: false)
}
