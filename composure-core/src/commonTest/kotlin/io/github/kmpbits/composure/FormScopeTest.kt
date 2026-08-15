package io.github.kmpbits.composure

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormScopeTest {

    private fun scope() = FormScope(CoroutineScope(SupervisorJob()))

    // ── isValid initial state ─────────────────────────────────────────────────

    @Test
    fun `isValid is false before any interaction`() {
        val form = scope()
        form.field("email", Email)
        assertFalse(form.isValid.value)
    }

    @Test
    fun `isValid becomes true once all fields pass validation`() {
        val form = scope()
        val email = form.field("email", Email)
        email.onChange("joel@kmpbits.io")
        assertTrue(form.isValid.value)
    }

    // ── save / restore keyed by name ──────────────────────────────────────────

    @Test
    fun `saveFieldData keys by user-provided name`() {
        val form = scope()
        val email = form.field("email", Email)
        email.onChange("joel@kmpbits.io")

        val saved = form.saveFieldData()
        assertTrue(saved.containsKey("email"))
        assertEquals("joel@kmpbits.io", saved["email"]?.get(0))
    }

    @Test
    fun `restoreFieldData restores value by name`() {
        val saved = mapOf("email" to listOf("joel@kmpbits.io", null, "true", "true"))

        val form = scope()
        form.field("email", Email)
        form.restoreFieldData(saved)

        val emailField = form.get<Email>("email")
        assertEquals("joel@kmpbits.io", emailField.value.value)
        assertTrue(emailField.isTouched.value)
    }

    @Test
    fun `restoreFieldData ignores unknown keys gracefully`() {
        val form = scope()
        form.field("email", Email)
        form.restoreFieldData(mapOf("nonexistent" to listOf("value", null, "false", "false")))
        assertEquals("", form.get<Email>("email").value.value)
    }

    // ── isDirty ───────────────────────────────────────────────────────────────

    @Test
    fun `field isDirty is false before any change`() {
        val form = scope()
        val email = form.field("email", Email)
        assertFalse(email.isDirty.value)
    }

    @Test
    fun `field isDirty becomes true after onChange`() {
        val form = scope()
        val email = form.field("email", Email)
        email.onChange("joel@kmpbits.io")
        assertTrue(email.isDirty.value)
    }

    @Test
    fun `form isDirty reflects any field being dirty`() {
        val form = scope()
        val email = form.field("email", Email)
        assertFalse(form.isDirty.value)
        email.onChange("joel@kmpbits.io")
        assertTrue(form.isDirty.value)
    }

    // ── individual field reset ────────────────────────────────────────────────

    @Test
    fun `field reset clears value and state`() {
        val form = scope()
        val email = form.field("email", Email)
        email.onChange("joel@kmpbits.io")
        email.onBlur()
        email.reset()
        assertEquals("", email.value.value)
        assertFalse(email.isDirty.value)
        assertFalse(email.isTouched.value)
        assertEquals(null, email.error.value)
    }

    @Test
    fun `field reset does not affect other fields`() {
        val form = scope()
        val email = form.field("email", Email)
        val password = form.field(Password)
        email.onChange("joel@kmpbits.io")
        password.onChange("Secret1A")
        email.reset()
        assertEquals("", email.value.value)
        assertEquals("Secret1A", password.value.value)
    }

    // ── optional field validation ─────────────────────────────────────────────

    @Test
    fun `optional field is valid when blank`() {
        val form = scope()
        val website = form.field(Email) { optional() }
        website.onChange("")
        assertEquals(null, website.error.value)
        assertTrue(website.isValid)
    }

    @Test
    fun `optional field validates when non-blank`() {
        val form = scope()
        val website = form.field(Email) { optional() }
        website.onChange("notanemail")
        assertEquals("Enter a valid email address", website.error.value)
    }

    @Test
    fun `optional field clears error when blanked again`() {
        val form = scope()
        val website = form.field(Email) { optional() }
        website.onChange("notanemail")
        assertFalse(website.isValid)
        website.onChange("")
        assertTrue(website.isValid)
    }

    @Test
    fun `optional field does not block form submission`() {
        val form = scope()
        val email = form.field("email", Email)
        form.field(Email) { optional() } // optional website field
        email.onChange("joel@kmpbits.io")
        // form should be valid even with optional field untouched
        assertTrue(form.isValid.value)
    }

    // ── custom messages ───────────────────────────────────────────────────────

    @Test
    fun `messages overrides default required message on Email`() {
        val form  = scope()
        val email = form.field(Email) { messages(required = "Email is required") }
        email.onChange("")
        email.onBlur()
        // submit to trigger validateAll
        val submitAction = form.handleSubmit { }
        submitAction()
        assertEquals("Email is required", email.error.value)
    }

    @Test
    fun `messages overrides format message on Email`() {
        val form  = scope()
        val email = form.field(Email) { messages(format = "Not a valid email") }
        email.onChange("notanemail")
        assertEquals("Not a valid email", email.error.value)
    }

    @Test
    fun `messages overrides required message on Password`() {
        val form     = scope()
        val password = form.field(Password) { messages(required = "Password is required") }
        password.onChange("") // ON_CHANGE trigger runs required() synchronously
        assertEquals("Password is required", password.error.value)
    }

    @Test
    fun `messages does not affect extra validators added in the builder`() {
        val form  = scope()
        val email = form.field(Email) {
            messages(required = "Email is required", format = "Bad email")
        }
        email.onChange("good@example.com")
        assertEquals(null, email.error.value)
    }

    // ── dependsOn / mustMatch ─────────────────────────────────────────────────

    @Test
    fun `mustMatch shows error when confirm differs from password`() {
        val form = scope()
        val password = form.field(Password)
        val confirm = form.field(Password) { mustMatch(password) }

        password.onChange("Secret1")
        confirm.onChange("different")
        confirm.onBlur()

        assertEquals("Passwords do not match", confirm.error.value)
    }

    @Test
    fun `mustMatch clears error when values match`() {
        val form = scope()
        val password = form.field(Password)
        val confirm = form.field(Password) { mustMatch(password) }

        password.onChange("Secret1")
        confirm.onChange("Secret1")
        confirm.onBlur()

        assertEquals(null, confirm.error.value)
    }

    @Test
    fun `mustMatch re-validates confirm when password changes`() {
        val form = scope()
        val password = form.field(Password)
        val confirm = form.field(Password) { mustMatch(password) }

        // User fills both fields with matching values
        password.onChange("Secret1")
        confirm.onChange("Secret1")
        confirm.onBlur()
        assertEquals(null, confirm.error.value)

        // User goes back and changes password — confirm should now show an error
        password.onChange("Different1")
        assertEquals("Passwords do not match", confirm.error.value)
    }

    @Test
    fun `mustMatch does not show error on untouched confirm when password changes`() {
        val form = scope()
        val password = form.field(Password)
        val confirm = form.field(Password) { mustMatch(password) }

        password.onChange("Secret1")
        // confirm never touched — should stay silent
        assertEquals(null, confirm.error.value)
    }
}
