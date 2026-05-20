package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.EqPreset
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.ui.theme.GbeduPlayerTheme
import com.example.ui.theme.GbeduThemeType
import com.example.ui.viewmodel.MusicViewModel
import java.io.File
import kotlin.math.floor
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MusicViewModel = viewModel()
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

            GbeduPlayerTheme(themeType = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GbeduAppContainer(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GbeduAppContainer(viewModel: MusicViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Songs, 1: Playlists, 2: Equalizer, 3: Settings

    // Now Playing Panel visibility state
    var isNowPlayingExpanded by remember { mutableStateOf(false) }

    // Media scan permissions trigger
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanMedia()
            Toast.makeText(context, "Storage scanner started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Storage permission declined. Playing built-in dynamic synth sounds offline.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        // Request storage permissions on load to scan actual tracks if they exist
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(perm)
        } else {
            viewModel.scanMedia()
        }
    }

    // Capture physical Android back press inside fullscreen player to collapse nicely
    if (isNowPlayingExpanded) {
        BackHandler {
            isNowPlayingExpanded = false
        }
    }

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        bottomBar = {
            Column {
                // Mini Player Bar (Slides up when a track is loaded)
                AnimatedVisibility(
                    visible = currentTrack != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    MiniPlayerBar(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onNext = { viewModel.skipToNext() },
                        onClick = { isNowPlayingExpanded = true }
                    )
                }

                // Main navigation bar containing songs, playlist, equalizer and setting tabs
                NavigationBar(
                    modifier = Modifier.testTag("app_navigation_bar"),
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.MusicNote, contentDescription = "Songs") },
                        label = { Text("Tracks", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_tracks")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = "Playlists") },
                        label = { Text("Playlists", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_playlists")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.Tune, contentDescription = "Equalizer") },
                        label = { Text("Equalizer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_equalizer")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen switching based on selection
            when (selectedTab) {
                0 -> SongsScreen(viewModel = viewModel)
                1 -> PlaylistsScreen(viewModel = viewModel)
                2 -> EqualizerScreen(viewModel = viewModel)
                3 -> SettingsScreen(viewModel = viewModel)
            }

            // Fullscreen Now Playing Overlay
            AnimatedVisibility(
                visible = isNowPlayingExpanded,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)
                ) + fadeOut()
            ) {
                currentTrack?.let { track ->
                    NowPlayingPanel(
                        track = track,
                        viewModel = viewModel,
                        onCollapse = { isNowPlayingExpanded = false }
                    )
                }
            }
        }
    }
}

// ==========================================
// TABS & SCREENS IMPLEMENTATIONS
// ==========================================

@Composable
fun SongsScreen(viewModel: MusicViewModel) {
    val tracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks else {
            tracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Upper Title block
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Your Library",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "${tracks.size} tracks cached offline",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Row {
                IconButton(
                    onClick = { showSearch = !showSearch },
                    modifier = Modifier.testTag("songs_search_toggle")
                ) {
                    Icon(
                        imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = { viewModel.scanMedia() },
                    modifier = Modifier.testTag("songs_scan_trigger")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search text field
        AnimatedVisibility(visible = showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Search songs, artists...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("songs_search_field"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { showSearch = false })
            )
        }

        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Scanning storage media...", fontSize = 13.sp)
                }
            }
        }

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = "No music",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No tracks found",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Initialize demo synthesized songs below or upload audio.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.scanMedia() }) {
                        Text("Seed Ambient Synth Sounds")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("tracks_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredTracks, key = { it.id }) { track ->
                    TrackRowItem(
                        track = track,
                        isPlayingNow = currentTrack?.id == track.id,
                        modifier = Modifier.testTag("track_item_${track.id}"),
                        onPlay = { viewModel.playTrack(track, filteredTracks) },
                        onFavoriteToggle = { viewModel.toggleFavorite(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrackRowItem(
    track: Track,
    isPlayingNow: Boolean,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlayingNow) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art placeholder / visualizer indicator
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPlayingNow) {
                    // Small simple live EQ-bars inside Art for extreme polish
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val infiniteTransition = rememberInfiniteTransition()
                        val h1 by infiniteTransition.animateFloat(
                            initialValue = 0.2f, targetValue = 0.9f,
                            animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse)
                        )
                        val h2 by infiniteTransition.animateFloat(
                            initialValue = 0.3f, targetValue = 0.7f,
                            animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse)
                        )
                        val h3 by infiniteTransition.animateFloat(
                            initialValue = 0.1f, targetValue = 0.8f,
                            animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse)
                        )

                        Spacer(modifier = Modifier.weight(1f).fillMaxHeight(h1).background(Color.White).width(3.dp))
                        Spacer(modifier = Modifier.weight(1f).fillMaxHeight(h2).background(Color.White).width(3.dp))
                        Spacer(modifier = Modifier.weight(1f).fillMaxHeight(h3).background(Color.White).width(3.dp))
                    }
                } else {
                    Icon(
                        imageVector = if (track.isDemo) Icons.Rounded.GraphicEq else Icons.Rounded.Audiotrack,
                        contentDescription = "Song Artwork",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.isDemo) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("SYNTH", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = track.artist,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right column: duration / favorite status
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatDuration(track.duration),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsScreen(viewModel: MusicViewModel) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val playlistSongs by viewModel.playlistTracks.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayedTracks.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var showAddSongsDialog by remember { mutableStateOf(false) }

    if (selectedPlaylist != null) {
        // RENDER PLAYLIST DETAILS SELECTION
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.selectPlaylist(null) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedPlaylist?.name ?: "Playlist",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${playlistSongs.size} tracks",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedPlaylist?.isSmart == false) {
                    Button(
                        onClick = { showAddSongsDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Add Tracks", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (playlistSongs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("This playlist has no songs yet", fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(playlistSongs) { song ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playTrack(song, playlistSongs) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Audiotrack, contentDescription = "Audio")
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(song.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (selectedPlaylist?.isSmart == false) {
                                    IconButton(onClick = { viewModel.removeTrackFromPlaylist(selectedPlaylist!!, song) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Tracks Popup Dialog
        if (showAddSongsDialog && selectedPlaylist != null) {
            Dialog(onDismissRequest = { showAddSongsDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(420.dp).padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Choose Tracks to Add", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(allTracks.filter { track -> !playlistSongs.any { it.id == track.id } }) { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addTrackToPlaylist(selectedPlaylist!!, track)
                                            showAddSongsDialog = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "add", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(track.artist, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { showAddSongsDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }

    } else {
        // LIST ALL STATIC SMART PLAYLISTS + CUSTOM USER PLAYLISTS
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Playlists", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Offline custom & dynamic smart mix", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_playlist_button")
                ) {
                    Icon(Icons.Default.AddBox, contentDescription = "Add Playlist", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Favourites Smart folder block
                item {
                    PlaylistFolderRow(
                        title = "Favorite Tracks",
                        songCount = favorites.size,
                        icon = Icons.Rounded.Favorite,
                        iconColor = Color.Red,
                        onClick = {
                            viewModel.selectPlaylist(Playlist(id = -10, name = "Favorites", isSmart = true))
                        }
                    )
                }

                // Most played smart folder block
                item {
                    PlaylistFolderRow(
                        title = "Most Played",
                        songCount = mostPlayed.size,
                        icon = Icons.Rounded.Equalizer,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            viewModel.selectPlaylist(Playlist(id = -20, name = "Most Played", isSmart = true))
                        }
                    )
                }

                // Recently added smart folder block
                item {
                    PlaylistFolderRow(
                        title = "Recently Added",
                        songCount = mostPlayed.take(8).size, // Fallback/Recently Added tracks list size
                        icon = Icons.Rounded.Schedule,
                        iconColor = MaterialTheme.colorScheme.secondary,
                        onClick = {
                            viewModel.selectPlaylist(Playlist(id = -30, name = "Recently Added", isSmart = true))
                        }
                    )
                }

                // Header for Custom Playlists
                if (playlists.isNotEmpty()) {
                    item {
                        Text(
                            "Custom Playlists",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }

                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectPlaylist(playlist) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = "Playlist FolderIcon", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }

        // CREATE CUSTOM PLAYLIST DIALOG
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create Playlist") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylist(newPlaylistName)
                                newPlaylistName = ""
                                showCreateDialog = false
                            }
                        },
                        modifier = Modifier.testTag("playlist_confirm_create")
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun PlaylistFolderRow(
    title: String,
    songCount: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("$songCount tracks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EqualizerScreen(viewModel: MusicViewModel) {
    val eqEnabled by viewModel.equalizerEnabled.collectAsStateWithLifecycle()
    val rawPresets by viewModel.presets.collectAsStateWithLifecycle()
    val currentPresetName by viewModel.currentPresetName.collectAsStateWithLifecycle()
    val activePreset = remember(rawPresets, currentPresetName) {
        rawPresets.find { it.name == currentPresetName } ?: EqPreset.PRESETS.first()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Equalizer", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("True Offline Sound Modulator", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Power button with visual colors
            Button(
                onClick = { viewModel.toggleEqualizer() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (eqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "ActiveState", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (eqEnabled) "ON" else "OFF", fontWeight = FontWeight.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Preset Selector Row
        var expandPresetDropdown by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedCard(
                onClick = { expandPresetDropdown = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sound Profile: $currentPresetName", fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                }
            }

            DropdownMenu(
                expanded = expandPresetDropdown,
                onDismissRequest = { expandPresetDropdown = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                rawPresets.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.name, fontWeight = if (p.name == currentPresetName) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            viewModel.setEqPreset(p)
                            expandPresetDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Professional Equalizer Slider Board representation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .alpha(if (eqEnabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val freqLabels = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
            val rawBands = listOf(activePreset.band1, activePreset.band2, activePreset.band3, activePreset.band4, activePreset.band5)

            freqLabels.forEachIndexed { idx, label ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = rawBands[idx],
                        onValueChange = { newVal ->
                            if (eqEnabled) viewModel.updateEqSlider(idx, newVal)
                        },
                        valueRange = -15f..15f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("eq_slider_$idx")
                            .graphicsLayer {
                                rotationZ = -90f // Turn vertical
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                            }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${rawBands[idx].toInt()} dB",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        // Bass Boost & Virtualizer representation dials
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp)
                .alpha(if (eqEnabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Deep Bass Boost", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = activePreset.bassBoost,
                        onValueChange = { if (eqEnabled) viewModel.updateBassBoost(it) },
                        valueRange = 0f..100f
                    )
                    Text("${activePreset.bassBoost.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Spatial Virtualizer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = activePreset.virtualizer,
                        onValueChange = { if (eqEnabled) viewModel.updateVirtualizer(it) },
                        valueRange = 0f..100f
                    )
                    Text("${activePreset.virtualizer.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MusicViewModel) {
    val crossfadeSec by viewModel.crossfadeSec.collectAsStateWithLifecycle()
    val sleepMin by viewModel.sleepMinutesLeft.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Audio Pitch Settings", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Personalize Theme Dynamics & Timers", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(20.dp))

        // Color Theme selection
        Text("Visual Theme", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val themes = listOf(
                Triple(GbeduThemeType.AURA_OBSIDIAN, "Obsidian OLED Black", "Deepest pitch black with luxurious gold"),
                Triple(GbeduThemeType.NORDIC_FROST, "Nordic Frost Blue", "Ice cool clean blues with dark slate gray"),
                Triple(GbeduThemeType.SUNSET_COPPER, "Sunset Mahogany", "Warm copper ambers and rich espresso"),
                Triple(GbeduThemeType.NEON_CYBER, "Cyber Synthwave", "Dark purple with vibrant laser pink & neon cyan")
            )

            themes.forEach { (type, name, desc) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.setTheme(type) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentTheme == type) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        }
                    ),
                    border = if (currentTheme == type) CardDefaults.outlinedCardBorder() else null
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == type,
                            onClick = { viewModel.setTheme(type) }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Gapless & Crossfade Slider settings
        Text("Audio Crossfade Transition", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Duration", fontSize = 13.sp)
                    Text(if (crossfadeSec == 0) "Gapless Playback" else "$crossfadeSec seconds", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Slider(
                    value = crossfadeSec.toFloat(),
                    onValueChange = { viewModel.setCrossfade(it.toInt()) },
                    valueRange = 0f..5f,
                    steps = 4
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sleep Timer setup
        Text("Sleep Shut-off Timer", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (sleepMin != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active timer: $sleepMin min remaining", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { viewModel.cancelSleepTimer() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                        ) {
                            Text("Stop")
                        }
                    }
                } else {
                    Text("Shut off playback automatically after specified minutes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 15, 30, 60).forEach { mins ->
                            Button(
                                onClick = { viewModel.startSleepTimer(mins) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("${mins}m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// NOW PLAYING SHEET & MINI PLAYER BAR
// ==========================================

@Composable
fun MiniPlayerBar(
    track: Track?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    if (track == null) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .testTag("mini_player"),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Small Rotating Disc art
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                // Spinning transition simulation
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun NowPlayingPanel(
    track: Track,
    viewModel: MusicViewModel,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val position by viewModel.currentPosition.collectAsStateWithLifecycle()
    val visualizerBands by viewModel.visualizerBands.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val playQueue by viewModel.currentQueue.collectAsStateWithLifecycle()

    var showPlaylistAddDialog by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("now_playing_panel")
    ) {
        // Fullscreen dynamic blurred glass background matching theme color scheme gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .blur(80.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Upper toolbar block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                IconButton(onClick = { showPlaylistAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.12f))

            // Large vinyl sleeve layout / Album Cover with floating animations
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Floating record design
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.4f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.08f))

            // Track title and artist with favoriting trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(0.85f)) {
                    Text(
                        text = track.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = track.artist,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleFavorite(track) },
                    modifier = Modifier.weight(0.15f)
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.05f))

            // Interactive Modern 16-band Spectrum Visualizer reacting beautifully to playback frequencies
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                InteractiveMusicVisualizer(bands = visualizerBands, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.weight(0.05f))

            // Progress tracking Slider with exact timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progress,
                    onValueChange = { viewModel.seekTo(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("now_playing_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuration(position), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    Text(text = formatDuration(track.duration), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.weight(0.08f))

            // Core control dock: Shuffle, Prev, Play, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Huge Play/Pause card floating
                ElevatedCard(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(68.dp)
                        .scale(1f),
                    shape = CircleShape,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                    val iconColor = if (repeatMode != "NONE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (repeatMode == "ONE") Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = iconColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Bottom fast accessories: Queue drawer button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { showQueueDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "QueueIcon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Queue List", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // POPUP ADD TO PLAYLIST DIALOG
        if (showPlaylistAddDialog) {
            val userPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
            Dialog(onDismissRequest = { showPlaylistAddDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(350.dp).padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Add to Playlist", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (userPlaylists.isEmpty()) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text("No custom playlists found. Go back and create one.")
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(userPlaylists) { p ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.addTrackToPlaylist(p, track)
                                                showPlaylistAddDialog = false
                                                Toast.makeText(context, "Added to ${p.name}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.QueueMusic, contentDescription = null)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(p.name, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { showPlaylistAddDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        // POPUP QUEUE DRAMA DIALOG
        if (showQueueDialog) {
            Dialog(onDismissRequest = { showQueueDialog = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(400.dp).padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Current Play Queue", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(playQueue) { qTrack ->
                                val isCurrent = qTrack.id == track.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Rounded.PlayArrow else Icons.Rounded.Audiotrack,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(qTrack.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(qTrack.artist, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { showQueueDialog = false },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// CUSTOM COMPONENT DRAWERS
// ==========================================

@Composable
fun InteractiveMusicVisualizer(bands: FloatArray, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val spacing = 8.dp.toPx()
        val totalWidth = size.width
        val barCount = bands.size
        val availableWidth = totalWidth - (spacing * (barCount - 1))
        val barWidth = availableWidth / barCount

        for (i in 0 until barCount) {
            val magnitude = bands[i]
            val barHeight = magnitude * size.height
            val x = i * (barWidth + spacing)
            val y = size.height - barHeight

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

// Helper formats: maps milliseconds integer to minutes:seconds description string
fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}
