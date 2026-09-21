package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.model.Channel
import com.example.data.model.CloudUploadUnavailableException
import com.example.data.model.LiveStream
import com.example.data.model.MediaHostingStatus
import com.example.data.model.ShortItem
import com.example.data.model.Video
import com.example.di.AppContainer
import com.example.ui.theme.*
import com.example.ui.viewmodel.CreateUploadViewModel
import com.example.ui.viewmodel.CreateUploadViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUploadScreen(
    container: AppContainer,
    onUploadComplete: (Video) -> Unit = {},
    onCreateChannelClick: () -> Unit = {},
    onGoLiveStarted: (LiveStream) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CreateUploadViewModel = viewModel(factory = CreateUploadViewModelFactory(container))
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val currentChannel by container.channelRepository.currentChannel.collectAsState()
    val currentUser by container.authRepository.currentUserState.collectAsState()

    val uploadProgress by container.videoRepository.uploadProgress.collectAsState()
    val uploadStatus by container.videoRepository.uploadStatus.collectAsState()
    val uploadError by container.videoRepository.uploadError.collectAsState()

    val creationMode by viewModel.creationMode.collectAsState()

    // Upload Success / Preview State from ViewModel
    val uploadedVideoResult by viewModel.uploadedVideoResult.collectAsState()
    val uploadedShortResult by viewModel.uploadedShortResult.collectAsState()
    val localPreviewVideoResult by viewModel.localPreviewVideoResult.collectAsState()
    val localPreviewShortResult by viewModel.localPreviewShortResult.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val mediaHostingStatus by viewModel.mediaHostingStatus.collectAsState()
    val inaccessiblePreviewMessage by viewModel.inaccessiblePreviewMessage.collectAsState()

    // Video Form State from ViewModel
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsState()
    val selectedVideoName by viewModel.selectedVideoName.collectAsState()
    val selectedVideoResolution by viewModel.selectedVideoResolution.collectAsState()
    val videoTitle by viewModel.videoTitle.collectAsState()
    val videoDescription by viewModel.videoDescription.collectAsState()
    val videoCategory by viewModel.videoCategory.collectAsState()
    var tagInput by remember { mutableStateOf("") }
    val tagList by viewModel.tagList.collectAsState()
    var videoVisibility by remember { mutableStateOf("PUBLIC") } // PUBLIC, UNLISTED, PRIVATE
    var allowComments by remember { mutableStateOf(true) }
    val videoThumbnailUrl by viewModel.videoThumbnailUrl.collectAsState()
    var customThumbnailUrlInput by remember { mutableStateOf("") }
    var showCustomThumbnailDialog by remember { mutableStateOf(false) }

    // Short Form State from ViewModel
    val selectedShortUri by viewModel.selectedShortUri.collectAsState()
    val shortTitle by viewModel.shortTitle.collectAsState()
    val shortSound by viewModel.shortSound.collectAsState()
    val shortCategory by viewModel.shortCategory.collectAsState()
    var shortVisibility by remember { mutableStateOf("PUBLIC") }
    var shortAllowComments by remember { mutableStateOf(true) }
    val shortThumbnailUrl by viewModel.shortThumbnailUrl.collectAsState()

    // Live Stream Form State
    var liveTitle by remember { mutableStateOf("") }
    var liveCategory by remember { mutableStateOf("Gaming") }
    var liveResolution by remember { mutableStateOf("1080p60 Low Latency") }

    val context = LocalContext.current

    // Media Pickers using zero-permission Android Photo Picker with URI persistable permission grant
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Ignore if not supported by provider
            }
            viewModel.onVideoSelected(uri)
        }
    }

    val thumbnailPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onThumbnailSelected(uri)
        }
    }

    val shortPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) {
                // Ignore if not supported by provider
            }
            viewModel.onShortSelected(uri)
        }
    }

    val categories = listOf("Technology", "Gaming", "Entertainment", "Food", "Music", "Science", "Education", "Sports", "Lifestyle")
    val presetThumbnails = listOf(
        "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800" to "Futuristic AI",
        "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800" to "Cinema IMAX",
        "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=800" to "Esports Arena",
        "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=800" to "Artisan Gourmet",
        "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800" to "3D Hologram",
        "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800" to "Next-Gen Gaming"
    )

    val presetSounds = listOf(
        "Trending Synthwave Beat",
        "Cyberpunk Neon Drift",
        "Original Audio - Studio Mix",
        "Satisfying ASMR Kitchen",
        "Bass Heavy Trap Beats",
        "Lo-Fi Chill Hop 2026"
    )

    if (showCustomThumbnailDialog) {
        AlertDialog(
            onDismissRequest = { showCustomThumbnailDialog = false },
            title = { Text("Enter Custom Thumbnail URL", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = customThumbnailUrlInput,
                    onValueChange = { customThumbnailUrlInput = it },
                    placeholder = { Text("https://example.com/thumbnail.jpg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customThumbnailUrlInput.isNotBlank()) {
                            viewModel.setVideoThumbnailUrl(customThumbnailUrlInput)
                        }
                        showCustomThumbnailDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed)
                ) {
                    Text("Apply URL")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomThumbnailDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 90.dp)
        ) {
            // Screen Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(IombgRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Create +",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
                        )
                    }
                    Text(
                        text = "Publish 4K videos, Shorts, and broadcast live streams",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                currentChannel?.let { ch ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            AsyncImage(
                                model = ch.profileImageUrl,
                                contentDescription = ch.channelName,
                                modifier = Modifier.size(22.dp).clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(ch.handle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channel Check Guard
            if (currentChannel == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IombgRed.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(IombgRed.copy(alpha = 0.15f), Color.Transparent)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideoCall, contentDescription = null, tint = IombgRed, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Channel Required to Publish",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You need an active IOMBG creator channel to upload videos, create shorts, and broadcast live streams.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onCreateChannelClick,
                                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("create_channel_prompt_button")
                            ) {
                                Text("Create Your Channel", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Mode Selector: 1. Upload Video | 2. Create Short | 3. Go Live
            TabRow(
                selectedTabIndex = creationMode,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                divider = {}
            ) {
                Tab(
                    selected = creationMode == 0,
                    onClick = {
                        viewModel.setCreationMode(0)
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload Video", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_upload_video")
                )
                Tab(
                    selected = creationMode == 1,
                    onClick = {
                        viewModel.setCreationMode(1)
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create Short", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_create_short")
                )
                Tab(
                    selected = creationMode == 2,
                    onClick = {
                        viewModel.setCreationMode(2)
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Go Live", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_go_live")
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Warning banner if a previously saved local preview was inaccessible after process recreation
            inaccessiblePreviewMessage?.let { warnMsg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).testTag("inaccessible_preview_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = IombgGold.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IombgGold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = IombgGold, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Local Media Notice", fontWeight = FontWeight.Bold, color = IombgGold, fontSize = 13.sp)
                            Text(warnMsg, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                        }
                        IconButton(onClick = { viewModel.clearInaccessibleWarning() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = IombgGold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Global Media Hosting Status Badge
            if (mediaHostingStatus != MediaHostingStatus.NONE) {
                MediaHostingStatusBadge(
                    status = mediaHostingStatus,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            // Upload Progress & Cancel Indicator
            uploadProgress?.let { progress ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = IombgRed
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(uploadStatus.ifBlank { "Processing video upload..." }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text("$progress%", fontWeight = FontWeight.Black, color = IombgRed, fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            color = IombgRed,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.cancelUpload()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = IombgRed),
                                modifier = Modifier.testTag("cancel_upload_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel Upload", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Upload Error & Retry State
            uploadError?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Local Preview State (Cloud Upload Unavailable)
            localPreviewVideoResult?.let { video ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("local_preview_video_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IombgGold.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(IombgGold.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = IombgGold, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = IombgGold.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "LOCAL PREVIEW ONLY",
                                color = IombgGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Cloud Upload Unavailable", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Cloud video upload is currently unavailable. Your video is available for local preview only.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(video.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Local source: ${video.videoUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onUploadComplete(video) },
                                modifier = Modifier.weight(1f).testTag("watch_local_preview_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Local Preview", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.dismissLocalPreviewVideo()
                                },
                                modifier = Modifier.weight(1f).testTag("dismiss_local_preview_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Select Another", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Upload Complete State (Cloud Hosted Video)
            uploadedVideoResult?.let { video ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusSuccess.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(StatusSuccess, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Upload Complete & Published!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onUploadComplete(video) },
                                modifier = Modifier.weight(1f).testTag("watch_uploaded_video_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Watch Now", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString("https://iombg.tv/watch/${video.videoId}"))
                                },
                                modifier = Modifier.weight(1f).testTag("copy_video_link_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 1. UPLOAD VIDEO SCREEN FORM
            // ==========================================
            if (creationMode == 0 && uploadedVideoResult == null && localPreviewVideoResult == null) {
                // Step 1: Select Video & Video Preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = IombgRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("1. Select Video Source", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("select_device_video_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Device File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Selected Video Preview Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .testTag("selected_video_preview_box")
                        ) {
                            if (selectedVideoUri != null) {
                                AsyncImage(
                                    model = videoThumbnailUrl,
                                    contentDescription = "Video Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().alpha(0.7f)
                                )

                                // Overlay Play Badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                }

                                // Resolution & Quality Chip
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("local_video_selected_badge")
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(selectedVideoResolution, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No video selected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Tap 'Device File' above to pick a local video", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Metadata Templates
                        Text("Or choose a metadata template:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = videoCategory == "Technology" && videoTitle.contains("Supercomputers"),
                                onClick = {
                                    viewModel.setVideoTitle("The Next Generation of AI Supercomputers: Deep Architecture")
                                    viewModel.setVideoThumbnailUrl("https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800")
                                    viewModel.setVideoCategory("Technology")
                                    viewModel.setTagList(listOf("AI", "Tech", "Hardware", "4K"))
                                },
                                label = { Text("Tech 4K", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = videoCategory == "Entertainment" && videoTitle.contains("IMAX"),
                                onClick = {
                                    viewModel.setVideoTitle("How Christopher Nolan & Dune Masters Perfected IMAX Audio")
                                    viewModel.setVideoThumbnailUrl("https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800")
                                    viewModel.setVideoCategory("Entertainment")
                                    viewModel.setTagList(listOf("Cinema", "IMAX", "Sound", "Film"))
                                },
                                label = { Text("Cinema 4K", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = videoCategory == "Gaming" && videoTitle.contains("Cyberpunk"),
                                onClick = {
                                    viewModel.setVideoTitle("World Record Unreal Engine 5 Cyberpunk Heist S-Rank")
                                    viewModel.setVideoThumbnailUrl("https://images.unsplash.com/photo-1542751371-adc38448a05e?w=800")
                                    viewModel.setVideoCategory("Gaming")
                                    viewModel.setTagList(listOf("Gaming", "Speedrun", "Esports", "UE5"))
                                },
                                label = { Text("Gaming 4K", fontSize = 10.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 2: Select / Upload Thumbnail
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("2. Custom Thumbnail", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                            Row {
                                IconButton(
                                    onClick = {
                                        thumbnailPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.testTag("upload_thumbnail_image_button")
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Pick Image", tint = IombgRed)
                                }
                                IconButton(
                                    onClick = { showCustomThumbnailDialog = true },
                                    modifier = Modifier.testTag("upload_thumbnail_url_button")
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = "URL", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Preset Thumbnails Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(presetThumbnails) { (url, label) ->
                                val isSelected = videoThumbnailUrl == url
                                Column(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clickable { viewModel.setVideoThumbnailUrl(url) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) IombgRed else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(16.dp)
                                                    .background(IombgRed, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(label, fontSize = 10.sp, maxLines = 1, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 3: Video Details & Metadata
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("3. Video Metadata", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Title
                        OutlinedTextField(
                            value = videoTitle,
                            onValueChange = { if (it.length <= 100) viewModel.setVideoTitle(it) },
                            label = { Text("Video Title (Required)") },
                            supportingText = { Text("${videoTitle.length}/100") },
                            modifier = Modifier.fillMaxWidth().testTag("upload_video_title_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Description
                        OutlinedTextField(
                            value = videoDescription,
                            onValueChange = { viewModel.setVideoDescription(it) },
                            label = { Text("Description & Timestamps") },
                            placeholder = { Text("Tell viewers about your video, add chapters and links...") },
                            modifier = Modifier.fillMaxWidth().height(120.dp).testTag("upload_video_desc_input"),
                            shape = RoundedCornerShape(10.dp),
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category
                        Text("Category", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categories) { cat ->
                                FilterChip(
                                    selected = videoCategory == cat,
                                    onClick = { viewModel.setVideoCategory(cat) },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tags
                        Text("Tags & Search Keywords", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tagInput,
                                onValueChange = { tagInput = it },
                                placeholder = { Text("Add tag (e.g. Tutorial)", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f).testTag("tag_input_field"),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (tagInput.isNotBlank() && !tagList.contains(tagInput.trim())) {
                                        viewModel.setTagList(tagList + tagInput.trim())
                                        tagInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.testTag("add_tag_button")
                            ) {
                                Text("Add", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tag Chips
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(tagList) { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.setTagList(tagList.filter { it != tag }) },
                                    label = { Text("#$tag", fontSize = 11.sp) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 4: Visibility & Comments Settings
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("4. Visibility & Interaction Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Visibility Options: Public, Unlisted, Private
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            VisibilityOptionRow(
                                title = "Public",
                                subtitle = "Everyone can search for and view this video",
                                icon = Icons.Default.Public,
                                isSelected = videoVisibility == "PUBLIC",
                                onClick = { videoVisibility = "PUBLIC" }
                            )

                            VisibilityOptionRow(
                                title = "Unlisted",
                                subtitle = "Anyone with the video link can view",
                                icon = Icons.Default.Link,
                                isSelected = videoVisibility == "UNLISTED",
                                onClick = { videoVisibility = "UNLISTED" }
                            )

                            VisibilityOptionRow(
                                title = "Private",
                                subtitle = "Only you can view this video",
                                icon = Icons.Default.Lock,
                                isSelected = videoVisibility == "PRIVATE",
                                onClick = { videoVisibility = "PRIVATE" }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                        // Allow Comments Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Allow Comments", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Viewers can write comments and replies on your video", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = allowComments,
                                onCheckedChange = { allowComments = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IombgRed),
                                modifier = Modifier.testTag("allow_comments_switch")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action: Publish Video Button
                Button(
                    onClick = {
                        val channel = currentChannel ?: Channel(
                            channelId = "ch_${currentUser?.uid?.take(8) ?: "creator"}",
                            channelName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "My Channel",
                            handle = "@${currentUser?.username?.takeIf { it.isNotBlank() } ?: "creator"}",
                            profileImageUrl = currentUser?.photoUrl?.takeIf { it.isNotBlank() } ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                            ownerUid = currentUser?.uid ?: "user_default"
                        )
                        viewModel.publishLongVideo(
                            channel = channel,
                            visibility = videoVisibility,
                            allowComments = allowComments,
                            onComplete = { video -> onUploadComplete(video) }
                        )
                    },
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("publish_video_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publish 4K Video to IOMBG", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            // ==========================================
            // 2. CREATE SHORT SCREEN FORM
            // ==========================================
            if (creationMode == 1 && uploadedShortResult == null && localPreviewShortResult == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, tint = IombgGold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Vertical Short (9:16)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    shortPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("select_device_short_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Device Short", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Vertical Short Preview Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedShortUri != null) {
                                AsyncImage(
                                    model = shortThumbnailUrl,
                                    contentDescription = "Short Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().alpha(0.8f)
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier.size(50.dp).background(IombgRed, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Local Short Selected • 9:16 Vertical", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("local_short_selected_badge"))
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = IombgGold, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No Short video selected", fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Tap 'Device Short' above to choose a video", fontSize = 11.sp, color = Color.LightGray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = shortTitle,
                            onValueChange = { viewModel.setShortTitle(it) },
                            label = { Text("Short Title & #Hashtags") },
                            placeholder = { Text("Check this out! #Shorts #Trending") },
                            modifier = Modifier.fillMaxWidth().testTag("short_title_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Audio Track Selector
                        Text("Audio Track / Sound", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(presetSounds) { sound ->
                                FilterChip(
                                    selected = shortSound == sound,
                                    onClick = { viewModel.setShortSound(sound) },
                                    leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    label = { Text(sound, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category
                        Text("Category", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categories) { cat ->
                                FilterChip(
                                    selected = shortCategory == cat,
                                    onClick = { viewModel.setShortCategory(cat) },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Visibility
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Visibility: $shortVisibility", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Row {
                                FilterChip(
                                    selected = shortVisibility == "PUBLIC",
                                    onClick = { shortVisibility = "PUBLIC" },
                                    label = { Text("Public", fontSize = 11.sp) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = shortVisibility == "UNLISTED",
                                    onClick = { shortVisibility = "UNLISTED" },
                                    label = { Text("Unlisted", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Allow Comments on Short", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = shortAllowComments,
                                onCheckedChange = { shortAllowComments = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IombgRed)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val channel = currentChannel ?: Channel(
                            channelId = "ch_${currentUser?.uid?.take(8) ?: "creator"}",
                            channelName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "My Channel",
                            handle = "@${currentUser?.username?.takeIf { it.isNotBlank() } ?: "creator"}",
                            profileImageUrl = currentUser?.photoUrl?.takeIf { it.isNotBlank() } ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=400",
                            ownerUid = currentUser?.uid ?: "user_default"
                        )
                        viewModel.publishShort(
                            channel = channel,
                            visibility = shortVisibility,
                            allowComments = shortAllowComments,
                            onComplete = { short ->
                                val asVideo = Video(
                                    videoId = short.shortId,
                                    channelId = short.channelId,
                                    ownerUid = short.ownerUid,
                                    channelName = short.channelName,
                                    channelHandle = short.channelHandle,
                                    channelAvatarUrl = short.channelAvatarUrl,
                                    title = short.title,
                                    description = short.description,
                                    videoUrl = short.videoUrl,
                                    thumbnailUrl = short.thumbnailUrl,
                                    durationSeconds = short.durationSeconds,
                                    processingStatus = if (short.processingStatus == "LOCAL_PREVIEW") "LOCAL_PREVIEW" else "PUBLISHED"
                                )
                                onUploadComplete(asVideo)
                            }
                        )
                    },
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("publish_short_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publish Short to Feed", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            // Local Preview State (Cloud Upload Unavailable for Short)
            localPreviewShortResult?.let { short ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("local_preview_short_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IombgGold.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(IombgGold.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = IombgGold, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = IombgGold.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "LOCAL PREVIEW ONLY",
                                color = IombgGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Cloud Upload Unavailable", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Cloud video upload is currently unavailable. Your Short is available for local preview only.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(short.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Local source: ${short.videoUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val asVideo = Video(
                                        videoId = short.shortId,
                                        channelId = short.channelId,
                                        ownerUid = short.ownerUid,
                                        channelName = short.channelName,
                                        channelHandle = short.channelHandle,
                                        channelAvatarUrl = short.channelAvatarUrl,
                                        title = short.title,
                                        description = short.description,
                                        videoUrl = short.videoUrl,
                                        thumbnailUrl = short.thumbnailUrl,
                                        durationSeconds = short.durationSeconds,
                                        processingStatus = "LOCAL_PREVIEW"
                                    )
                                    onUploadComplete(asVideo)
                                },
                                modifier = Modifier.weight(1f).testTag("watch_local_preview_short_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview Short", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.dismissLocalPreviewShort()
                                },
                                modifier = Modifier.weight(1f).testTag("dismiss_local_preview_short_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Create Another", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Short Uploaded Success Card
            uploadedShortResult?.let { short ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusSuccess.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(StatusSuccess, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Short Published Successfully!", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(short.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                viewModel.dismissUploadedShort()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IombgRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Create Another Short", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ==========================================
            // 3. GO LIVE SCREEN FORM (Full GoLiveSetupScreen)
            // ==========================================
            if (creationMode == 2) {
                GoLiveSetupScreen(
                    container = container,
                    onLiveStarted = onGoLiveStarted,
                    onCreateChannelClick = onCreateChannelClick,
                    onBack = { viewModel.setCreationMode(0) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Visual badge indicating the true hosting state of a media item:
 * - LOCAL_PREVIEW: Local device content, not published to cloud
 * - UPLOADING: In-flight upload to cloud
 * - CLOUD_HOSTED: Live, production cloud hosted content
 * - UPLOAD_UNAVAILABLE: Cloud upload service is unavailable; preserved for local playback
 */
@Composable
fun MediaHostingStatusBadge(
    status: MediaHostingStatus,
    modifier: Modifier = Modifier
) {
    if (status == MediaHostingStatus.NONE) return

    val label: String
    val bgColor: Color
    val textColor: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector

    when (status) {
        MediaHostingStatus.LOCAL_PREVIEW -> {
            label = "LOCAL PREVIEW ONLY • NOT HOSTED ON CLOUD"
            bgColor = IombgGold.copy(alpha = 0.18f)
            textColor = IombgGold
            icon = Icons.Default.PhoneAndroid
        }
        MediaHostingStatus.UPLOADING -> {
            label = "UPLOADING TO CLOUD STORAGE..."
            bgColor = IombgBlue.copy(alpha = 0.18f)
            textColor = IombgBlue
            icon = Icons.Default.CloudUpload
        }
        MediaHostingStatus.CLOUD_HOSTED -> {
            label = "CLOUD HOSTED • LIVE"
            bgColor = StatusSuccess.copy(alpha = 0.18f)
            textColor = StatusSuccess
            icon = Icons.Default.CloudDone
        }
        MediaHostingStatus.UPLOAD_UNAVAILABLE -> {
            label = "LOCAL PREVIEW • CLOUD UPLOAD UNAVAILABLE"
            bgColor = IombgGold.copy(alpha = 0.22f)
            textColor = IombgGold
            icon = Icons.Default.Info
        }
        MediaHostingStatus.NONE -> return
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.5f)),
        modifier = modifier.testTag("media_hosting_status_badge_${status.name}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VisibilityOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) IombgRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, IombgRed) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) IombgRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isSelected) IombgRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = IombgRed)
            )
        }
    }
}

private fun selectedVideoCategory(category: String): String {
    return when (category) {
        "Technology" -> "Deep Tech Architecture & AI"
        "Gaming" -> "Pro Esports & Speedrun"
        "Entertainment" -> "IMAX Cinema Soundscape"
        "Food" -> "Artisan Gastronomy"
        else -> "Exclusive Premiere"
    }
}
