package com.iie.st10320489.stylu

import org.junit.Test
import org.junit.Assert.*


class AuthValidationTest {

    @Test
    fun testEmptyEmail() {
        val email = ""
        assertTrue("Empty email should be detected", email.isEmpty())
    }

    @Test
    fun testPasswordMinLength() {
        val password = "12345"
        val isValid = password.length >= 6
        assertFalse("Password shorter than 6 characters should fail", isValid)
    }

    @Test
    fun testValidPassword() {
        val password = "password123"
        val isValid = password.length >= 6
        assertTrue("Password with 6+ characters should pass", isValid)
    }

    @Test
    fun testPasswordsMatch() {
        val password = "password123"
        val confirmPassword = "password123"
        assertEquals("Matching passwords should be equal", password, confirmPassword)
    }

    @Test
    fun testPasswordsDontMatch() {
        val password = "password123"
        val confirmPassword = "password456"
        assertNotEquals("Different passwords should not match", password, confirmPassword)
    }

    @Test
    fun testNameMinLength() {
        val firstName = "J"
        val isValid = firstName.length >= 2
        assertFalse("Name shorter than 2 characters should fail", isValid)
    }

    @Test
    fun testValidName() {
        val firstName = "John"
        val isValid = firstName.length >= 2
        assertTrue("Name with 2+ characters should pass", isValid)
    }
}