package com.cropcompass.app.util

object Validation {
    val commodities = listOf("Tomato", "Onion", "Potato", "Maize", "Paddy")
    val districts = listOf("Bagalkot", "Ballari", "Belagavi", "Bengaluru Rural", "Bengaluru Urban", "Bidar", "Chamarajanagar", "Chikkaballapur", "Chikkamagaluru", "Chitradurga", "Dakshina Kannada", "Davanagere", "Dharwad", "Gadag", "Hassan", "Haveri", "Kalaburagi", "Kodagu", "Kolar", "Koppal", "Mandya", "Mysuru", "Raichur", "Bengaluru South", "Shivamogga", "Tumakuru", "Udupi", "Uttara Kannada", "Vijayapura", "Vijayanagara", "Yadgir")
    fun login(email: String, password: String): String? = when {
        !email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) -> "Enter a valid email address."
        password.isEmpty() -> "Enter your password."
        else -> null
    }
    fun registration(name: String, email: String, password: String, confirm: String, district: String): String? = when {
        name.trim().length !in 2..80 -> "Enter a full name between 2 and 80 characters."
        login(email, password) != null -> login(email, password)
        password.length < 8 -> "Use a password with at least 8 characters."
        password != confirm -> "Passwords do not match."
        district !in districts -> "Choose a Karnataka district."
        else -> null
    }
}
