import SwiftUI

struct RegistrationView: View {

    @StateObject private var form = RegistrationForm()
    @State private var accountCreated = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {

                Text("Create account").font(.largeTitle).bold()
                Text("Class-based approach — typed properties.\nTry: taken@example.com")
                    .font(.caption).foregroundStyle(.secondary)

                Spacer().frame(height: 16)

                ComposureField("Email", field: form.email)
                ComposureField("Password", field: form.password, isSecure: true)
                ComposureField("Confirm password", field: form.confirm, isSecure: true)

                Spacer().frame(height: 8)

                Button {
                    form.submit { accountCreated = true }
                } label: {
                    Group {
                        if form.isSubmitting { ProgressView() } else { Text("Create account") }
                    }
                    .frame(maxWidth: .infinity).frame(height: 52)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!form.isValid || form.isSubmitting)

                if let err = form.submitError {
                    Text(err).font(.caption).foregroundStyle(.red)
                }

                if accountCreated {
                    successBanner("Account created! Welcome aboard.")
                }
            }
            .padding(.horizontal, 24).padding(.vertical, 40)
            .animation(.easeInOut, value: accountCreated)
        }
    }
}

#Preview { NavigationStack { RegistrationView() } }
