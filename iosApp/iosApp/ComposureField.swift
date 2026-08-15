import SwiftUI

/// SwiftUI equivalent of `ComposureTextField` from the Compose sample.
///
/// Wires a `FieldState` to a SwiftUI `TextField` / `SecureField`, calling
/// `onChange` on every keystroke and `onBlur` when the user taps away.
/// Displays validation errors and an activity indicator while async
/// validators run — mirroring the Compose version exactly.
struct ComposureField: View {

    // MARK: – Inputs

    let label: String
    let value: String
    let error: String?
    let isValidating: Bool
    let isSecure: Bool
    let onValueChange: (String) -> Void
    let onBlur: () -> Void

    // MARK: – Focus state (for blur detection)

    @FocusState private var isFocused: Bool

    // MARK: – Body

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ZStack(alignment: .trailing) {
                if isSecure {
                    SecureField(
                        label,
                        text: .init(
                            get: { value },
                            set: { onValueChange($0) }
                        )
                    )
                    .textFieldStyle(.roundedBorder)
                    .focused($isFocused)
                    .onChange(of: isFocused) { _, focused in
                        if !focused { onBlur() }
                    }
                } else {
                    TextField(
                        label,
                        text: .init(
                            get: { value },
                            set: { onValueChange($0) }
                        )
                    )
                    .textFieldStyle(.roundedBorder)
                    .focused($isFocused)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .onChange(of: isFocused) { _, focused in
                        if !focused { onBlur() }
                    }
                }

                // Spinner while async validator is running
                if isValidating {
                    ProgressView()
                        .scaleEffect(0.75)
                        .padding(.trailing, 8)
                }
            }
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(borderColor, lineWidth: 1.5)
            )

            // Error message (mirrors Compose's supportingText)
            if let error, !error.isEmpty {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .padding(.leading, 4)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.15), value: error)
    }

    // MARK: – Helpers

    private var borderColor: Color {
        if error != nil && !error!.isEmpty { return .red }
        if isFocused { return .accentColor }
        return Color(.systemGray4)
    }
}
