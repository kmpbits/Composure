package io.github.kmpbits.composure

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposureFormScopeTest {

    @Test
    fun `emailField applies required and email format validators by default`() {
        val form = ComposureFormScope()
        val email = form.emailField()

        email.update("")
        assertEquals("This field is required", email.state.error.value)

        email.update("notanemail")
        assertEquals("Enter a valid email address", email.state.error.value)

        email.update("joel@kmpbits.io")
        assertNull(email.state.error.value)
    }

    @Test
    fun `emailField honors custom required and format messages`() {
        val form = ComposureFormScope()
        val email = form.emailField(requiredMessage = "Required!", formatMessage = "Bad format!")

        email.update("")
        assertEquals("Required!", email.state.error.value)

        email.update("notanemail")
        assertEquals("Bad format!", email.state.error.value)
    }

    @Test
    fun `emailField marked optional accepts a blank value`() {
        val form = ComposureFormScope()
        val email = form.emailField(optional = true)

        email.update("")
        assertNull(email.state.error.value)
    }

    @Test
    fun `passwordField enforces default strength rules`() {
        val form = ComposureFormScope()
        val password = form.passwordField()

        password.update("short")
        assertTrue(password.state.error.value != null)

        password.update("longenough1") // 11 chars, has a digit, no uppercase
        assertTrue(password.state.error.value != null)

        password.update("LongEnough1")
        assertNull(password.state.error.value)
    }

    @Test
    fun `passwordField relaxes strength rules when disabled`() {
        val form = ComposureFormScope()
        val password = form.passwordField(minLength = 0, requireUppercase = false, requireDigit = false)

        password.update("a")
        assertNull(password.state.error.value)
    }

    @Test
    fun `confirmField without a matching field has no validators`() {
        val form = ComposureFormScope()
        val confirm = form.confirmField()

        confirm.update("anything")
        assertNull(confirm.state.error.value)
    }

    @Test
    fun `confirmField matching another field validates equality and re-validates live`() {
        val form = ComposureFormScope()
        val password = form.passwordField()
        val confirm = form.confirmField(matching = password)

        password.update("Secret1A")
        confirm.update("different")
        assertEquals("Passwords do not match", confirm.state.error.value)

        confirm.update("Secret1A")
        assertNull(confirm.state.error.value)
    }

    @Test
    fun `update blur and reset delegate to the underlying field`() {
        val form = ComposureFormScope()
        val email = form.emailField()

        email.update("joel@kmpbits.io")
        email.blur()
        assertEquals("joel@kmpbits.io", email.state.value.value)
        assertTrue(email.state.isTouched.value)

        email.reset()
        assertEquals("", email.state.value.value)
        assertFalse(email.state.isTouched.value)
    }

    @Test
    fun `formScope isValid reflects the fields registered through the bridge`() {
        val form = ComposureFormScope()
        val email = form.emailField()

        assertFalse(form.formScope.isValid.value)

        email.update("joel@kmpbits.io")
        assertTrue(form.formScope.isValid.value)
    }

    @Test
    fun `asyncValidator bridges a synchronous valid callback into ValidationResult Valid`() = runTest {
        val form = ComposureFormScope()
        val validator = form.asyncValidator { _, callback -> callback.complete(null) }

        val result = validator.validate("anything")
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `asyncValidator bridges a synchronous invalid callback into ValidationResult Invalid`() = runTest {
        val form = ComposureFormScope()
        val validator = form.asyncValidator { _, callback -> callback.complete("Already taken") }

        val result = validator.validate("anything")
        assertEquals(ValidationResult.Invalid("Already taken"), result)
    }
}
