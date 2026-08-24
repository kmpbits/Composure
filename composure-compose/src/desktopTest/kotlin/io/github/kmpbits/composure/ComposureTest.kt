package io.github.kmpbits.composure

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `block variant returns a working FormScope with the declared named field`() {
        lateinit var capturedForm: FormScope

        composeTestRule.setContent {
            val form = rememberFormState {
                field("email", Email)
            }
            capturedForm = form
        }

        assertEquals("", capturedForm.get<Email>("email").value.value)
        assertFalse(capturedForm.isValid.value)
    }

    @Test
    fun `factory variant returns the object built by the factory, wired to a live FormScope`() {
        class LoginForm(scope: FormScope) : FormController by scope {
            val email = scope.field(Email)
        }

        lateinit var capturedForm: LoginForm

        composeTestRule.setContent {
            val form = rememberFormState { scope -> LoginForm(scope) }
            capturedForm = form
        }

        assertEquals("", capturedForm.email.value.value)
        assertFalse(capturedForm.isValid.value)
    }

    @Test
    fun `a field obtained from rememberFormState routes onChange back into form isValid`() {
        lateinit var capturedForm: FormScope

        composeTestRule.setContent {
            val form = rememberFormState {
                field("email", Email)
            }
            capturedForm = form
        }

        composeTestRule.runOnIdle {
            capturedForm.get<Email>("email").onChange("joel@kmpbits.io")
        }
        composeTestRule.waitForIdle()

        assertTrue(capturedForm.isValid.value)
    }

    @Test
    fun `rememberFormState keeps the same FormScope instance across recomposition`() {
        val recomposeTrigger = mutableStateOf(0)
        val capturedForms = mutableListOf<FormScope>()

        composeTestRule.setContent {
            recomposeTrigger.value
            val form = rememberFormState { field("email", Email) }
            capturedForms += form
        }

        composeTestRule.runOnIdle { recomposeTrigger.value = 1 }
        composeTestRule.waitForIdle()

        assertEquals(2, capturedForms.size)
        assertTrue(capturedForms[0] === capturedForms[1])
    }
}
