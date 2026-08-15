import SwiftUI

struct LoginView: View {

    @StateObject private var form = LoginForm()
    @State private var signedIn = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {

                Text("Sign in").font(.largeTitle).bold()
                Text("Inline approach — no form class needed.")
                    .font(.caption).foregroundStyle(.secondary)

                Spacer().frame(height: 16)

                ComposureField("Email", field: form.email)
                ComposureField("Password", field: form.password, isSecure: true)

                Spacer().frame(height: 8)

                Button {
                    form.submit { signedIn = true }
                } label: {
                    Group {
                        if form.isSubmitting { ProgressView() } else { Text("Sign in") }
                    }
                    .frame(maxWidth: .infinity).frame(height: 52)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!form.isValid || form.isSubmitting)

                if signedIn {
                    successBanner("Welcome back!")
                }
            }
            .padding(.horizontal, 24).padding(.vertical, 40)
            .animation(.easeInOut, value: signedIn)
        }
    }
}

#Preview { NavigationStack { LoginView() } }
