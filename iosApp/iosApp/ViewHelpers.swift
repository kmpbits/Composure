import SwiftUI

/// Green success card — reused by LoginView and RegistrationView.
func successBanner(_ message: String) -> some View {
    HStack {
        Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
        Text(message)
    }
    .padding()
    .frame(maxWidth: .infinity, alignment: .leading)
    .background(Color(.systemGreen).opacity(0.15))
    .clipShape(RoundedRectangle(cornerRadius: 10))
    .transition(.move(edge: .bottom).combined(with: .opacity))
}
