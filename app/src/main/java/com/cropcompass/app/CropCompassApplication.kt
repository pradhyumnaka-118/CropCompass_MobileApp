package com.cropcompass.app

import android.app.Application
import com.cropcompass.app.data.local.Preferences
import com.cropcompass.app.data.repository.AccountRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context
import androidx.room.Room
import com.cropcompass.app.data.local.MarketDatabase
import com.cropcompass.app.data.repository.MarketRepository
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class CropCompassApplication : Application() {
    @Inject lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); com.cropcompass.app.data.WorkScheduler.schedule(this) }
}
@Singleton
class AppContainer @Inject constructor(@ApplicationContext val context: Context) {
    private val firebase = FirebaseApp.initializeApp(context)
    val firestore: FirebaseFirestore? = firebase?.let { FirebaseFirestore.getInstance(it) }
    val accounts = AccountRepository(firebase?.let { FirebaseAuth.getInstance(it) }, firestore)
    val preferences = Preferences(context)
    val database = Room.databaseBuilder(context, MarketDatabase::class.java, "crop-compass.db").build()
    val market = MarketRepository(context, database)
    val remote = com.cropcompass.app.data.remote.RemoteRefresh(market)
}
