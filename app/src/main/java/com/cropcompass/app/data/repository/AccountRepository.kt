package com.cropcompass.app.data.repository

import com.cropcompass.app.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class AccountRepository(private val auth: FirebaseAuth?, private val db: FirebaseFirestore?) {
    val configured get() = auth != null && db != null
    val uid get() = auth?.currentUser?.uid
    suspend fun profile(): UserProfile? {
        val user = auth?.currentUser ?: return null
        val ref = requireNotNull(db).collection("users").document(user.uid)
        val cached = try { ref.get(Source.CACHE).await().takeIf { it.exists() } }
            catch (e: Exception) { if (e is CancellationException) throw e; null }
        val doc = cached ?: withTimeoutOrNull(10_000) { ref.get().await() }
            ?: error("Your profile is unavailable. Check your connection and try again.")
        check(doc.exists()) { "Your account profile is missing. Contact the project administrator." }
        return UserProfile(user.uid, doc.getString("name") ?: user.displayName.orEmpty(), user.email.orEmpty(), doc.getString("district") ?: "Kolar")
    }
    suspend fun login(email: String, password: String): UserProfile {
        requireNotNull(auth) { "Firebase is not configured. Use the guest demo or add google-services.json." }
            .signInWithEmailAndPassword(email.trim(), password).await()
        return requireNotNull(profile())
    }
    suspend fun register(name: String, email: String, password: String, district: String): UserProfile {
        val user = requireNotNull(auth) { "Firebase is not configured. Use the guest demo or add google-services.json." }
            .createUserWithEmailAndPassword(email.trim(), password).await().user ?: error("Registration did not return an account.")
        val profile = UserProfile(user.uid, name.trim(), email.trim(), district)
        try {
            requireNotNull(db).collection("users").document(user.uid).set(mapOf(
                "uid" to user.uid, "name" to profile.name, "email" to profile.email,
                "district" to district, "createdAt" to FieldValue.serverTimestamp()
            )).await()
        } catch (e: Exception) {
            // Remove the new auth account if profile creation fails, so registration can be retried.
            try { user.delete().await() } catch (_: Exception) { auth.signOut() }
            throw e
        }
        return profile
    }
    suspend fun updateDistrict(district: String) {
        require(district in com.cropcompass.app.util.Validation.districts)
        requireNotNull(db).collection("users").document(requireNotNull(uid)).update("district", district).await()
    }
    fun logout() { auth?.signOut() }
}
