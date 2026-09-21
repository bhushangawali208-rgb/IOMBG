package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.di.AppContainer
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private val PRESET_AVATARS = listOf(
    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400",
    "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
    "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=400",
    "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400",
    "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400"
)

private val PRESET_BANNERS = listOf(
    "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=1200",
    "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200",
    "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=1200",
    "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200"
)

private val CATEGORIES = listOf(
    "Entertainment",
    "Technology",
    "Gaming",
    "Music",
    "Education",
    "Comedy",
    "Lifestyle",
    "News & Politics",
    "Sports"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChannelScreen(
    container: AppContainer,
    onChannelCreated: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val currentUser by container.authRepository.currentUserState.collectAsState()

    var channelName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var handle by remember {
        val initialHandle = currentUser?.username?.let { "@$it" } ?: "@creator"
        mutableStateOf(initialHandle)
    }
    var description by remember { mutableStateOf("Welcome to my official IOMBG channel! Follow for new videos and daily shorts.") }
    var selectedCategory by remember { mutableStateOf("Technology") }
    var selectedAvatarUrl by remember { mutableStateOf(currentUser?.photoUrl.takeIf { !it.isNullOrBlank() } ?: PRESET_AVATARS[0]) }
    var selectedBannerUrl by remember { mutableStateOf(PRESET_BANNERS[0]) }

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedBannerUrl = uri.toString()
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUrl = uri.toString()
        }
    }

    var isCheckingHandle by remember { mutableStateOf(false) }
    var handleAvailable by remember { mutableStateOf<Boolean?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var handleError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun validateInputs(): Boolean {
        var isValid = true
        nameError = null
        handleError = null
        formError = null

        val trimmedName = channelName.trim()
        if (trimmedName.length < 2) {
            nameError = "Channel name must be at least 2 characters"
            isValid = false
        } else if (trimmedName.length > 50) {
            nameError = "Channel name must be under 50 characters"
            isValid = false
        }

        val rawHandle = handle.removePrefix("@").trim()
        val handleRegex = Regex("^[a-zA-Z0-9_]{3,25}$")
        if (rawHandle.length < 3) {
            handleError = "Handle must be at least 3 characters"
            isValid = false
        } else if (!handleRegex.matches(rawHandle)) {
            handleError = "Handle can only contain letters, numbers, and underscores"
            isValid = false
        }

        return isValid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Your Channel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Channel Banner Preview & Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Channel Banner", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            bannerPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("pick_channel_banner_button")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Photo", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = selectedBannerUrl,
                        contentDescription = "Banner Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESET_BANNERS.forEachIndexed { index, banner ->
                        val isSelected = selectedBannerUrl == banner
                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) IombgRed else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedBannerUrl = banner }
                        ) {
                            AsyncImage(
                                model = banner,
                                contentDescription = "Banner preset $index",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Profile Avatar Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Channel Avatar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            avatarPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("pick_channel_avatar_button")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Photo", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = selectedAvatarUrl,
                        contentDescription = "Selected Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(2.dp, IombgRed, CircleShape)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PRESET_AVATARS.forEachIndexed { index, avatar ->
                            val isSelected = selectedAvatarUrl == avatar
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) IombgRed else Color.White.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedAvatarUrl = avatar }
                            ) {
                                AsyncImage(
                                    model = avatar,
                                    contentDescription = "Avatar preset $index",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Channel Name Field
                OutlinedTextField(
                    value = channelName,
                    onValueChange = {
                        channelName = it
                        nameError = null
                    },
                    label = { Text("Channel Name *") },
                    placeholder = { Text("e.g. Nexus Media Studios") },
                    isError = nameError != null,
                    supportingText = {
                        nameError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        } ?: Text("Your public creator brand name (max 50 chars)")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("channel_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Channel Handle Field
                OutlinedTextField(
                    value = handle,
                    onValueChange = { input ->
                        val formatted = if (input.startsWith("@")) input else "@$input"
                        handle = formatted
                        handleError = null
                        handleAvailable = null
                    },
                    label = { Text("Channel Handle *") },
                    placeholder = { Text("@yourhandle") },
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                    trailingIcon = {
                        if (isCheckingHandle) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (handleAvailable == true) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Available", tint = StatusSuccess)
                        }
                    },
                    isError = handleError != null,
                    supportingText = {
                        handleError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        } ?: Text("Unique handle for your channel URL and mentions")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("channel_handle_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Selection
                Text("Primary Category *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CATEGORIES.forEach { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IombgRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Channel Description") },
                    placeholder = { Text("Tell viewers what your channel is about...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("channel_description_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Error alert
                formError?.let { err ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Submit Button
                Button(
                    onClick = {
                        if (validateInputs()) {
                            isSubmitting = true
                            formError = null
                            coroutineScope.launch {
                                try {
                                    val user = currentUser
                                    val ownerUid = user?.uid ?: "user_default"
                                    val cleanHandle = if (handle.startsWith("@")) handle else "@$handle"

                                    // Check uniqueness
                                    isCheckingHandle = true
                                    val isAvailable = container.channelRepository.isHandleAvailable(cleanHandle)
                                    isCheckingHandle = false

                                    if (!isAvailable) {
                                        handleError = "This handle is already taken. Please choose another."
                                        isSubmitting = false
                                        return@launch
                                    }

                                    // Create channel
                                    val createdChannel = container.channelRepository.createChannel(
                                        ownerUid = ownerUid,
                                        name = channelName,
                                        handle = cleanHandle,
                                        description = description,
                                        category = selectedCategory,
                                        profileImageUrl = selectedAvatarUrl,
                                        bannerImageUrl = selectedBannerUrl
                                    )

                                    if (createdChannel != null) {
                                        // Link channel to user account
                                        container.authRepository.linkChannelToUser(createdChannel.channelId)
                                        container.authRepository.dismissWelcomeOnboarding()
                                        onChannelCreated()
                                    } else {
                                        formError = "Failed to create channel. Please try again."
                                    }
                                } catch (e: Exception) {
                                    formError = e.localizedMessage ?: "Unexpected error creating channel"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_create_channel_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Channel & Enter Studio", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
