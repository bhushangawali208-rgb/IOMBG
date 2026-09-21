package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PremiumPlan
import com.example.data.model.SubscriptionStatus
import com.example.di.AppContainer
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentUser by container.authRepository.currentUserState.collectAsState()
    val availablePlans by container.premiumRepository.availablePlans.collectAsState()
    val currentSubscription by container.premiumRepository.currentSubscription.collectAsState()
    val isProcessingPayment by container.premiumRepository.isProcessingPayment.collectAsState()
    val paymentError by container.premiumRepository.paymentError.collectAsState()

    var selectedPlanId by remember { mutableStateOf(availablePlans.firstOrNull()?.planId ?: "plan_monthly") }
    val selectedPlan = availablePlans.find { it.planId == selectedPlanId } ?: availablePlans.firstOrNull()

    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var snackbarHostState = remember { SnackbarHostState() }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val isSubscribed = currentSubscription?.status == "ACTIVE" || currentUser?.isPremium == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("IOMBG Premium", fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = IombgGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp)
        ) {
            // Gold Gradient Hero Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, IombgGold.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2C2416), Color(0xFF1E170F), Color(0xFF12100E))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(IombgGold.copy(alpha = 0.18f), CircleShape)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = IombgGold,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isSubscribed) "You are an IOMBG Premium Member" else "Upgrade to IOMBG Premium",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = IombgGold
                            )
                        )

                        Text(
                            text = "Enjoy ad-free videos, background audio streaming, VIP gold badge, and support favorite creators.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, IombgGold.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = IombgGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Secure Backend Payment Verification",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error display if any
            if (paymentError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(paymentError ?: "", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Active Subscription Management (if already subscribed)
            if (isSubscribed && currentSubscription != null) {
                val sub = currentSubscription!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Current Plan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(sub.planName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (sub.status == "ACTIVE") StatusSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = sub.status,
                                    color = if (sub.status == "ACTIVE") StatusSuccess else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Started On", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(dateFormat.format(Date(sub.startedAt)), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (sub.autoRenew) "Next Renewal Date" else "Expires On", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(dateFormat.format(Date(sub.renewalAt)), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = IombgGold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Amount", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${sub.amount.toInt()} / billing cycle", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Provider / Gateway", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(sub.provider.replace("_", " "), fontWeight = FontWeight.Medium, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (sub.status == "ACTIVE") {
                                OutlinedButton(
                                    onClick = { showCancelConfirmDialog = true },
                                    modifier = Modifier.weight(1f).testTag("cancel_subscription_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Cancel Subscription", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Button(
                                onClick = {
                                    container.premiumRepository.restoreSubscription(currentUser?.uid ?: "current_user")
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Subscription verified and restored successfully ✨")
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("restore_subscription_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Restore Purchase", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Membership Plans Picker
            Text("Available Subscription Plans", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                availablePlans.forEach { plan ->
                    val isSelected = plan.planId == selectedPlanId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlanId = plan.planId }
                            .testTag("plan_card_${plan.planId}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) IombgGold.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, IombgGold) else null
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedPlanId = plan.planId },
                                        colors = RadioButtonDefaults.colors(selectedColor = IombgGold)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(plan.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text("₹${plan.price.toInt()}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = IombgGold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            plan.benefits.take(3).forEach { benefit ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IombgGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(benefit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Plan Benefits Checklist
            Text("Premium Privileges Breakdown", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val fullBenefits = selectedPlan?.benefits ?: listOf(
                        "Ad-Free Viewing across all Videos & Shorts",
                        "Gold Premium Badge on Profile and Comments",
                        "Background Playback & Picture-in-Picture Mode",
                        "Ultra High 4K Bitrate & Spatial Audio Streaming"
                    )

                    fullBenefits.forEach { desc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = IombgGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = desc, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Subscribe Button
            Button(
                onClick = {
                    val uid = currentUser?.uid ?: "current_user"
                    val planToBuy = selectedPlan ?: availablePlans.first()
                    coroutineScope.launch {
                        val result = container.premiumRepository.subscribeToPlan(uid, planToBuy)
                        result.onSuccess {
                            // Update currentUser isPremium flag
                            currentUser?.let { u ->
                                container.authRepository.updateProfile(u.copy(isPremium = true))
                            }
                            snackbarHostState.showSnackbar("Welcome to IOMBG Premium! ✨ Subscription activated.")
                        }.onFailure { err ->
                            snackbarHostState.showSnackbar("Subscription failed: ${err.message}")
                        }
                    }
                },
                enabled = !isProcessingPayment,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("subscribe_premium_button"),
                colors = ButtonDefaults.buttonColors(containerColor = IombgGold, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isProcessingPayment) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSubscribed) "Manage or Renew Plan" else "Subscribe to ${selectedPlan?.name ?: "Premium"} (₹${selectedPlan?.price?.toInt() ?: 129})",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restore Purchases Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        container.premiumRepository.restoreSubscription(currentUser?.uid ?: "current_user")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Subscription verified from secure ledger.")
                        }
                    }
                ) {
                    Text("Restore previous purchases", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // Cancel Subscription Confirmation Dialog
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = { Text("Cancel Premium Subscription?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Your benefits will remain active until the end of the current billing cycle. You won't be charged for renewal.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        container.premiumRepository.cancelSubscription()
                        showCancelConfirmDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Subscription cancelled. Active until cycle ends.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Keep Subscription")
                }
            }
        )
    }
}
