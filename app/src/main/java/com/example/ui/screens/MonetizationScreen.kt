package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ApplicationStatus
import com.example.data.model.Channel
import com.example.di.AppContainer
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonetizationScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentChannel by container.channelRepository.currentChannel.collectAsState()
    val applicationState by container.monetizationRepository.applicationState.collectAsState()

    var legalName by remember { mutableStateOf("Alex Rivera") }
    var panNumber by remember { mutableStateOf("ABCDE1234F") }
    var isTermsAccepted by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    val channel = currentChannel ?: Channel(
        channelId = "ch_01",
        channelName = "Nexus Media Studios",
        handle = "@nexusmedia",
        subscriberCount = 14200,
        totalWatchHours = 6420.5,
        totalViews = 1850000
    )

    val subsProgress = (channel.subscriberCount / 500f).coerceIn(0f, 1f)
    val watchHoursProgress = ((channel.totalWatchHours / 500.0).toFloat()).coerceIn(0f, 1f)
    val shortsViewsProgress = ((channel.totalViews / 100000.0).toFloat()).coerceIn(0f, 1f)

    val meetsCriteria = channel.subscriberCount >= 500 && (channel.totalWatchHours >= 500.0 || channel.totalViews >= 100000)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monetization & Partner Program", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
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
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (meetsCriteria) IombgGold.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (meetsCriteria) Icons.Default.Stars else Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = if (meetsCriteria) IombgGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (meetsCriteria) "Eligible for monetization" else "Not eligible yet",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (meetsCriteria) {
                                    "You meet the channel milestone criteria for the IOMBG Partner Program."
                                } else {
                                    "Continue creating content to reach the partner milestone requirements below."
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Partner Program Requirements
            Text("Eligibility Requirements", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(10.dp))

            // Subscriber Goal (Mandatory for both)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1. Community Followers", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${channel.subscriberCount} / 500 followers", fontWeight = FontWeight.Bold, color = if (channel.subscriberCount >= 500) StatusSuccess else IombgRed, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { subsProgress },
                        color = if (channel.subscriberCount >= 500) StatusSuccess else IombgRed,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pathway 1: Long Videos Watch Time
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pathway A: Long Video Watch Hours", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${String.format("%.1f", channel.totalWatchHours)} / 500 hrs", fontWeight = FontWeight.Bold, color = if (channel.totalWatchHours >= 500) StatusSuccess else IombgRed, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { watchHoursProgress },
                        color = if (channel.totalWatchHours >= 500) StatusSuccess else IombgRed,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Eligible public watch hours in the last 12 months.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pathway 2: Shorts Views
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pathway B: Public Shorts Views", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${channel.totalViews} / 100K views", fontWeight = FontWeight.Bold, color = if (channel.totalViews >= 100000) StatusSuccess else IombgRed, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { shortsViewsProgress },
                        color = if (channel.totalViews >= 100000) StatusSuccess else IombgRed,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Eligible public shorts views in the last 90 days.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Transparent Revenue Share Breakdown
            Text("Revenue Share Model", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = IombgRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Video & Shorts Ad Revenue: 55% Creator Share", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text("55% of all gross ad revenue generated on your videos is allocated directly to your creator pool.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = IombgGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thanks / Creator Support: 70% Creator Share", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text("Direct fan contributions and super support in live streams provide you with 70% gross payout from day one.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Monetization Application Status
            Text("Monetization Application", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Monetization application is currently unavailable.",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Creator applications and formal KYC onboarding will open when the IOMBG monetization system is officially launched.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
