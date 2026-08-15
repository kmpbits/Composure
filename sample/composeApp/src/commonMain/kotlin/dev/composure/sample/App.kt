package io.github.kmpbits.composure.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.kmpbits.composure.AsyncFieldValidator
import io.github.kmpbits.composure.Email
import io.github.kmpbits.composure.FormController
import io.github.kmpbits.composure.FormScope
import io.github.kmpbits.composure.Password
import io.github.kmpbits.composure.ValidationResult
import io.github.kmpbits.composure.async
import io.github.kmpbits.composure.hasDigit
import io.github.kmpbits.composure.hasUppercase
import io.github.kmpbits.composure.messages
import io.github.kmpbits.composure.minLength
import io.github.kmpbits.composure.mustMatch
import io.github.kmpbits.composure.rememberFormState
import kotlinx.coroutines.delay

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold {
            Surface(
                modifier = Modifier.fillMaxSize()
                    .padding(it)
            ) {
                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf("Login (inline)", "Register (class)")

                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title) },
                            )
                        }
                    }
                    when (selectedTab) {
                        0 -> LoginScreen()
                        1 -> RegistrationScreen()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Inline approach — no form class, string keys, quick to write
// ---------------------------------------------------------------------------

@Composable
fun LoginScreen() {
    val form = rememberFormState {
        field("email", Email)
        field("password", Password)
    }

    val isValid by form.isValid.collectAsState()
    val isSubmitting by form.isSubmitting.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Sign in",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Inline approach — no form class needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        ComposureTextField(
            field = form["email"],
            label = "Email",
            modifier = Modifier.fillMaxWidth()
        )
        ComposureTextField(
            field = form["password"],
            label = "Password",
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Done,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = form.handleSubmit { delay(1000) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = isValid && !isSubmitting,
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Sign in")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Class-based approach — typed properties, compile-time safety, reusable
// ---------------------------------------------------------------------------

class RegistrationForm(scope: FormScope) : FormController by scope {

    private val checkEmailAvailability = AsyncFieldValidator { value ->
        delay(800)
        if (value.lowercase() == "taken@example.com") ValidationResult.Invalid("Already registered")
        else ValidationResult.Valid
    }

    val email = scope.field(Email) {
        messages(required = "Email is required", format = "Enter a valid email address")
        async(checkEmailAvailability)
    }

    val password = scope.field(Password) {
        messages(required = "Password is required")
        minLength(8)
        hasUppercase()
        hasDigit()
    }

    val confirm = scope.field(Password) {
        messages(required = "Please confirm your password")
        mustMatch(password)
    }
}

@Composable
fun RegistrationScreen() {
    val form = rememberFormState { scope -> RegistrationForm(scope) }
    var submitted by remember { mutableStateOf(false) }

    val isValid by form.isValid.collectAsState()
    val isSubmitting by form.isSubmitting.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Create account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Class-based approach — typed properties, compile-time safety.\nTry: taken@example.com",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        ComposureTextField(field = form.email, label = "Email", modifier = Modifier.fillMaxWidth())
        ComposureTextField(
            field = form.password,
            label = "Password",
            modifier = Modifier.fillMaxWidth()
        )

        ComposureTextField(
            field = form.confirm,
            label = "Confirm password",
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Done,
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = form.handleSubmit { delay(1500); submitted = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = isValid && !isSubmitting,
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Create account")
            }
        }

        if (submitted) {
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    "Account created! Welcome aboard.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
