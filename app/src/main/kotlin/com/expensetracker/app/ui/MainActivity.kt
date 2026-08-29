package com.expensetracker.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.ui.dashboard.DashboardScreen
import com.expensetracker.app.ui.onboarding.PermissionsBanner
import com.expensetracker.app.ui.onboarding.hasNotificationPostPermission
import com.expensetracker.app.ui.onboarding.hasSmsPermission
import com.expensetracker.app.ui.onboarding.isNotificationListenerEnabled
import com.expensetracker.app.ui.settings.SettingsScreen
import com.expensetracker.app.ui.theme.ExpenseTrackerTheme
import com.expensetracker.app.ui.transactions.TransactionListScreen

private sealed class Destination(val route: String, val label: String) {
    data object Dashboard : Destination("dashboard", "Dashboard")
    data object Transactions : Destination("transactions", "Transactions")
    data object Settings : Destination("settings", "Settings")
}

class MainActivity : ComponentActivity() {
    private val factory by lazy { AppViewModelFactory(ExpenseTrackerApp.from(this)) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* state re-read on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openTransactionId = intent.getLongExtra(EXTRA_OPEN_TRANSACTION_ID, -1L).takeIf { it >= 0 }

        setContent {
            ExpenseTrackerTheme {
                var smsGranted by remember { mutableStateOf(hasSmsPermission(this)) }
                var notifPostGranted by remember { mutableStateOf(hasNotificationPostPermission(this)) }
                var notifListenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(this)) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            smsGranted = hasSmsPermission(this@MainActivity)
                            notifPostGranted = hasNotificationPostPermission(this@MainActivity)
                            notifListenerEnabled = isNotificationListenerEnabled(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Column {
                    PermissionsBanner(
                        smsGranted = smsGranted,
                        notificationPostGranted = notifPostGranted,
                        notificationListenerEnabled = notifListenerEnabled,
                        onRequestSms = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                        },
                        onRequestNotificationPost = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        },
                    )
                    AppScaffold(factory, openTransactionId)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_TRANSACTION_ID = "open_transaction_id"
    }
}

@Composable
private fun AppScaffold(factory: AppViewModelFactory, initialTransactionId: Long?) {
    val navController = rememberNavController()
    val destinations = listOf(Destination.Dashboard, Destination.Transactions, Destination.Settings)

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(iconFor(destination), contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (initialTransactionId != null) Destination.Transactions.route else Destination.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Dashboard.route) { DashboardScreen(factory) }
            composable(Destination.Transactions.route) { TransactionListScreen(factory, initialTransactionId) }
            composable(Destination.Settings.route) { SettingsScreen(factory) }
        }
    }
}

private fun iconFor(destination: Destination) = when (destination) {
    Destination.Dashboard -> Icons.Filled.Home
    Destination.Transactions -> Icons.Filled.List
    Destination.Settings -> Icons.Filled.Settings
}
