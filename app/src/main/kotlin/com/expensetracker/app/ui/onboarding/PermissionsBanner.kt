package com.expensetracker.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED

fun hasNotificationPostPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/**
 * Automatic capture is the whole point of this app — without SMS read access and Notification
 * Access granted, nothing works. This banner stays visible (rather than a one-time onboarding
 * screen) since a user can revoke either permission at any time from system settings.
 */
@Composable
fun PermissionsBanner(
    smsGranted: Boolean,
    notificationPostGranted: Boolean,
    notificationListenerEnabled: Boolean,
    onRequestSms: () -> Unit,
    onRequestNotificationPost: () -> Unit,
) {
    if (smsGranted && notificationPostGranted && notificationListenerEnabled) return
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Automatic capture needs a few permissions",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!smsGranted) {
                Text("• SMS access — to read bank transaction alerts (parsed on-device only).")
                TextButton(onClick = onRequestSms) { Text("Grant SMS access") }
            }
            if (!notificationPostGranted) {
                Text("• Notification permission — so we can alert you after each transaction.")
                TextButton(onClick = onRequestNotificationPost) { Text("Grant notification permission") }
            }
            if (!notificationListenerEnabled) {
                Text("• Notification Access — to catch GPay/PhonePe/Paytm payments that don't send SMS.")
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Open Notification Access settings") }
            }
        }
    }
}
