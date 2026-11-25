package com.cropcompass.app

import com.cropcompass.app.util.Validation
import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun validRegistration() { assertNull(Validation.registration("Farmer Name", "farmer@example.com", "password123", "password123", "Kolar")) }
    @Test fun rejectsInvalidFields() {
        assertNotNull(Validation.login("invalid", "secret"))
        assertNotNull(Validation.login("a@b.com", ""))
        assertNotNull(Validation.registration("A", "a@b.com", "password123", "password123", "Kolar"))
        assertNotNull(Validation.registration("Farmer", "a@b.com", "short", "short", "Kolar"))
        assertNotNull(Validation.registration("Farmer", "a@b.com", "password123", "different", "Kolar"))
        assertNotNull(Validation.registration("Farmer", "a@b.com", "password123", "password123", "Outside Karnataka"))
    }
}
