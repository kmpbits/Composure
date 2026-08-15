import SwiftUI

/// Root view — mirrors the TabRow in the Compose sample.
/// Tab 0 = inline approach (LoginView), Tab 1 = class-based approach (RegistrationView).
struct ContentView: View {
    var body: some View {
        TabView {
            LoginView()
                .tabItem {
                    Label("Sign In", systemImage: "person.fill")
                }

            RegistrationView()
                .tabItem {
                    Label("Register", systemImage: "person.badge.plus")
                }
        }
    }
}

#Preview {
    ContentView()
}
