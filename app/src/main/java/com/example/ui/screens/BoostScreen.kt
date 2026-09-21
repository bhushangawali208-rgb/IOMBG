package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.di.AppContainer
import com.example.ui.components.formatCount
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoostScreen(
    container: AppContainer,
    preSelectedVideo: Video?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val videos by container.videoRepository.videosFeed.collectAsState()
    val shorts by container.videoRepository.shortsFeed.collectAsState()
    val boostOptions by container.boostRepository.boostOptions.collectAsState()
    val boostCampaigns by container.boostRepository.campaigns.collectAsState()
    val currentChannel by container.channelRepository.currentChannel.collectAsState()
    val currentUser by container.authRepository.currentUserState.collectAsState()
    val isProcessing by container.boostRepository.isProcessing.collectAsState()
    val errorMessage by container.boostRepository.error.collectAsState()

    var selectedContentType by remember { mutableStateOf("VIDEO") } // "VIDEO" or "SHORT"
    var selectedVideo by remember { mutableStateOf(preSelectedVideo ?: videos.firstOrNull()) }
    var selectedShort by remember { mutableStateOf(shorts.firstOrNull()) }

    var selectedOptionId by remember { mutableStateOf(boostOptions.getOrNull(1)?.optionId ?: "opt_growth") }
    val selectedOption = boostOptions.find { it.optionId == selectedOptionId } ?: boostOptions.firstOrNull()

    var customBudgetSlider by remember { mutableFloatStateOf(selectedOption?.budget?.toFloat() ?: 999f) }
    var durationDays by remember { mutableIntStateOf(selectedOption?.durationDays ?: 7) }
    var selectedCategory by remember { mutableStateOf("All") }

    var showCampaignDetailsDialog by remember { mutableStateOf<BoostCampaign?>(null) }
    var showBoostComingSoonDialog by remember { mutableStateOf(false) }
    var snackbarHostState = remember { SnackbarHostState() }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Dynamic calculations based on slider
    val calculatedImpressionsMin = (customBudgetSlider * 10).toLong()
    val calculatedImpressionsMax = (customBudgetSlider * 18).toLong()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("IOMBG Boost System", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = IombgRed, modifier = Modifier.size(22.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Intro Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, IombgRed.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF2D1214), Color(0xFF1E1012))))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(IombgRed.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = IombgRed,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Accelerate Channel Growth", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = IombgRed)
                                Text("Promote videos and Shorts to discovery feeds with verified impression delivery.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }

            // Compliance & Non-Manipulation Notice
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = IombgGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Transparent Boost Policy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = IombgGold
                            )
                            Text(
                                text = "Boost delivers prioritized distribution impressions to high-intent audiences and labels content with a 'BOOSTED' badge. We do not guarantee arbitrary views or manipulate organic quality scores.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Create New Campaign Form
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Create New Boost Campaign", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Content Type Switch (Videos vs Shorts)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedContentType == "VIDEO",
                                onClick = { selectedContentType = "VIDEO" },
                                label = { Text("Long Video") },
                                leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f).testTag("select_video_boost_tab")
                            )
                            FilterChip(
                                selected = selectedContentType == "SHORT",
                                onClick = { selectedContentType = "SHORT" },
                                label = { Text("Shorts") },
                                leadingIcon = { Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f).testTag("select_short_boost_tab")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Selected Content Picker
                        Text("Selected Content for Boost:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))

                        if (selectedContentType == "VIDEO") {
                            selectedVideo?.let { v ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, IombgRed.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = v.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(v.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                            Text("${formatCount(v.viewCount)} views • ${v.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        } else {
                            selectedShort?.let { s ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, IombgRed.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = s.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(s.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                            Text("${formatCount(s.viewCount)} views • Shorts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Configurable Boost Options Presets
                        Text("Configurable Package Presets:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            boostOptions.forEach { opt ->
                                val isOptSelected = opt.optionId == selectedOptionId
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isOptSelected) IombgRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (isOptSelected) IombgRed else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedOptionId = opt.optionId
                                            customBudgetSlider = opt.budget.toFloat()
                                            durationDays = opt.durationDays
                                        }
                                        .testTag("boost_option_${opt.optionId}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(opt.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${opt.durationDays} Days Duration • ${opt.estimatedReach}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("₹${opt.budget.toInt()}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = IombgRed)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Budget Fine-Tuning Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Campaign Budget", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("₹${customBudgetSlider.toInt()} ($durationDays Days)", fontWeight = FontWeight.Black, fontSize = 14.sp, color = IombgRed)
                        }

                        Slider(
                            value = customBudgetSlider,
                            onValueChange = { customBudgetSlider = it },
                            valueRange = 299f..10000f,
                            steps = 19,
                            colors = SliderDefaults.colors(thumbColor = IombgRed, activeTrackColor = IombgRed),
                            modifier = Modifier.testTag("boost_budget_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Projected Reach Estimate Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Estimated Priority Impressions:", fontSize = 12.sp)
                                    Text("${formatCount(calculatedImpressionsMin)} - ${formatCount(calculatedImpressionsMax)}", fontWeight = FontWeight.Bold, color = StatusSuccess, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Est. Organic Engagement Views:", fontSize = 12.sp)
                                    Text("${(calculatedImpressionsMin * 0.22).toInt()} - ${(calculatedImpressionsMax * 0.28).toInt()}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (showBoostComingSoonDialog) {
                            AlertDialog(
                                onDismissRequest = { showBoostComingSoonDialog = false },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = IombgRed, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Boost is coming soon", fontWeight = FontWeight.Bold)
                                    }
                                },
                                text = {
                                    Column {
                                        Text(
                                            "Paid promotion is not available yet.",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Content boosting and campaign advertising will be available after the production launch of the IOMBG monetization system.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { showBoostComingSoonDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Got it", fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }

                        Button(
                            onClick = {
                                showBoostComingSoonDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("launch_boost_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Launch Boost Campaign", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Active & Past Boost Campaigns Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Boost Campaigns Dashboard", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${boostCampaigns.size} Total",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (boostCampaigns.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No active boost campaigns.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            items(boostCampaigns) { campaign ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCampaignDetailsDialog = campaign }
                        .testTag("campaign_card_${campaign.campaignId}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = IombgRed.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "BOOSTED",
                                        color = IombgRed,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(campaign.contentTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (campaign.status) {
                                    BoostStatus.ACTIVE -> StatusSuccess.copy(alpha = 0.2f)
                                    BoostStatus.PAUSED -> IombgGold.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                }
                            ) {
                                Text(
                                    text = campaign.status.name,
                                    color = when (campaign.status) {
                                        BoostStatus.ACTIVE -> StatusSuccess
                                        BoostStatus.PAUSED -> IombgGold
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Delivered Impressions", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatCount(campaign.deliveredImpressions)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("Generated Views", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatCount(campaign.generatedViews)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = IombgRed)
                            }
                            Column {
                                Text("Budget", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${campaign.budgetAmount.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("Clicks", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatCount(campaign.generatedClicks)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons: Pause / Resume / Cancel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (campaign.status == BoostStatus.ACTIVE) {
                                TextButton(
                                    onClick = { container.boostRepository.pauseCampaign(campaign.campaignId) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = IombgGold)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pause", fontSize = 11.sp)
                                }
                            } else if (campaign.status == BoostStatus.PAUSED) {
                                TextButton(
                                    onClick = { container.boostRepository.resumeCampaign(campaign.campaignId) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = StatusSuccess)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume", fontSize = 11.sp)
                                }
                            }

                            TextButton(
                                onClick = { container.boostRepository.cancelCampaign(campaign.campaignId) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("End Campaign", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Campaign Full Performance Dialog
    if (showCampaignDetailsDialog != null) {
        val c = showCampaignDetailsDialog!!
        AlertDialog(
            onDismissRequest = { showCampaignDetailsDialog = null },
            title = { Text("Campaign Telemetry", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.contentTitle, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Campaign ID:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c.campaignId, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Target Category:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c.targetCategory, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimated Reach:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(c.estimatedImpressions, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Delivered Impressions:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatCount(c.deliveredImpressions)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusSuccess)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Views Generated:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatCount(c.generatedViews)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IombgRed)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showCampaignDetailsDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}
