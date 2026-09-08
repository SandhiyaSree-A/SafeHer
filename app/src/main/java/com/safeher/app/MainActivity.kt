package com.safeher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.safeher.app.data.repository.AuthRepository
import com.safeher.app.data.offline.OfflineSyncWorker
import com.safeher.app.ui.auth.AuthViewModel
import com.safeher.app.ui.navigation.SafeHerNavGraph
import com.safeher.app.ui.theme.SafeHerTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authViewModel.checkExistingSession()
        scheduleOfflineSync()

        setContent {
            SafeHerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    SafeHerNavGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        onSignOut = {
                            AuthRepository().signOut()
                        }
                    )
                }
            }
        }
    }

    private fun scheduleOfflineSync() {
        val request = PeriodicWorkRequestBuilder<OfflineSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "safeher_offline_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
