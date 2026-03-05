package com.rootilabs.wmeCardiac.ui.main

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rootilabs.wmeCardiac.R
import com.rootilabs.wmeCardiac.ui.theme.*
import com.rootilabs.wmeCardiac.ui.history.HistoryScreen
import com.rootilabs.wmeCardiac.ui.profile.ProfileScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import kotlinx.coroutines.launch
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.util.Log
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onViewHistory: () -> Unit,
    onViewProfile: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState = viewModel.uiState
    var isDrawerOpen by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe lifecycle events to refresh status on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("MainScreen", "ON_RESUME: Refreshing recording status...")
                viewModel.checkRecordingStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) onLogout()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // Speech Recognition Launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.setVoiceInputActive(false) // 語音結果回來，解除鎖定
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = data?.get(0) ?: ""
            if (spokenText.isNotBlank()) {
                viewModel.setOtherSymptom(spokenText)
            }
        }
    }

    val startVoiceInput = {
        viewModel.setVoiceInputActive(true) // 語音開始，鎖定 cancelTagFlow
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "請點選麥克風後說出您的症狀")
        }
        speechRecognizerLauncher.launch(intent)
    }

    val drawerWidthDp = 280.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val drawerWidthPx = with(density) { drawerWidthDp.toPx() }
    val drawerOffsetPx = remember { Animatable(0f) }
    // 同步 isDrawerOpen 狀態給遮罩使用
    val isOpen = drawerOffsetPx.value > drawerWidthPx * 0.5f

    // 個人資料關閉後，重置抽屜位置
    LaunchedEffect(showProfile) {
        if (!showProfile) {
            drawerOffsetPx.snapTo(0f)
            isDrawerOpen = false
        }
    }

    // 最外層：讓個人資料可以覆蓋在上方
    Box(modifier = Modifier.fillMaxSize()) {

    // Push Drawer 外層容器
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFDFE0DF))) {

        // ── 抽屜面板（固定在左側）──
        Column(
            modifier = Modifier
                .width(drawerWidthDp)
                .fillMaxHeight()
                .background(Color(0xFF7C7C7C))
        ) {
            // 抽屜標題列
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF7C7C7C))
                    .statusBarsPadding()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.system_settings),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // 個人資料選項（全寬，正常高度）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(com.rootilabs.wmeCardiac.ui.theme.TagGoGreen)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) {
                        showProfile = true
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.profile_icon_normal),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.personal_profile),
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

        }

        // ── 主畫面（往右推，隨手指即時移動）──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { androidx.compose.ui.unit.IntOffset(drawerOffsetPx.value.toInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newOffset = (drawerOffsetPx.value + delta)
                            .coerceIn(0f, drawerWidthPx)
                        scope.launch { drawerOffsetPx.snapTo(newOffset) }
                    },
                    onDragStopped = {
                        scope.launch {
                            val target = if (drawerOffsetPx.value > drawerWidthPx * 0.4f)
                                drawerWidthPx else 0f
                            drawerOffsetPx.animateTo(
                                target,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            isDrawerOpen = target > 0f
                        }
                    }
                )
        ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
                val tagFlowOpen = uiState.tagFlowStep != TagFlowStep.IDLE
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val density = androidx.compose.ui.platform.LocalDensity.current

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color.Black)
                ) {
                    val fullHeightPx = with(density) { maxHeight.toPx() }

                    // Linear progress calculation based on sheet offset and screen height
                    val progress by remember(tagFlowOpen) {
                        derivedStateOf {
                            if (!tagFlowOpen) 0f
                            else {
                                val offset = try { sheetState.requireOffset() } catch (e: Exception) { fullHeightPx }
                                val p = 1f - (offset / fullHeightPx).coerceIn(0f, 1f)
                                (p / 0.92f).coerceIn(0f, 1f)
                            }
                        }
                    }

                    val mainScale = 1f - (0.10f * progress)
                    val mainOffset = (5.dp.value * progress).dp

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(y = mainOffset)
                            .scale(mainScale)
                            .clip(
                                if (tagFlowOpen)
                                    androidx.compose.foundation.shape.RoundedCornerShape((24 * progress).dp)
                                else
                                    androidx.compose.ui.graphics.RectangleShape
                            )
                    ) {
                        // Green Header (按鈕在這裡，跟著縮放動畫一起移動)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TagGoGreen)
                        ) {
                            Column {
                                Spacer(modifier = Modifier.statusBarsPadding())
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // 選單按鈕（左側）
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                drawerOffsetPx.animateTo(
                                                    drawerWidthPx,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                                isDrawerOpen = true
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.CenterStart)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.btn_left_menu_normal),
                                            contentDescription = "Menu",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    // 標題（置中）
                                    Text(
                                        text = stringResource(id = R.string.digital_tagging),
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Main content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFFE8E8E8)),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val currentTime = remember { mutableStateOf("") }
                            val currentDate = remember { mutableStateOf("") }
                            LaunchedEffect(Unit) {
                                var counter = 0
                                while (true) {
                                    val now = Date()
                                    currentTime.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                                    currentDate.value = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
                                    if (counter % 3 == 0) {
                                        viewModel.checkRecordingStatus()
                                        viewModel.loadEventTags()
                                    }
                                    counter++
                                    kotlinx.coroutines.delay(1000)
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f)) // 上方空白，讓內容垂直置中

                            Text(
                                text = currentTime.value,
                                fontSize = 96.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF797979)
                            )
                            Text(
                                text = currentDate.value,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF797979),
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            Text(
                                text = if (uiState.isMeasuring) stringResource(id = R.string.tap_to_tag) else stringResource(id = R.string.no_recording),
                                fontSize = 24.sp,
                                color = Color(0xFF797979),
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            TagButton(
                                isMeasuring = uiState.isMeasuring,
                                onClick = { viewModel.onTagPressed() },
                                modifier = Modifier
                                    .offset(y = (-10).dp)
                                    .zIndex(1f) // Ensure button is on top of the image below
                            )

                            Image(
                                painter = painterResource(id = R.drawable.img_rx),
                                contentDescription = "Device",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(1.2f)
                                    .offset(y = (-20).dp),
                                contentScale = ContentScale.Fit
                            )

                            Spacer(modifier = Modifier.weight(1f))
                        }

                        // Bottom bar (Footer)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF888888))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(id = R.string.last_tag), color = Color(0xFFE0E0E0), fontSize = 14.sp)
                                Text(
                                    text = uiState.lastTagTime ?: stringResource(id = R.string.no_records),
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box {
                                Button(
                                    onClick = onViewHistory,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555)),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Text(stringResource(id = R.string.view), color = Color.White, fontSize = 16.sp)
                                }
                                if (uiState.showSyncErrorBadge) {
                                    Image(
                                        painter = painterResource(id = R.drawable.icon_warning2),
                                        contentDescription = "Sync Error",
                                        modifier = Modifier
                                            .size(26.dp)
                                            .align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-8).dp)
                                    )
                                }
                            }
                        }
                    } // end main Column

                    // Tag Flow Overlay
                    if (tagFlowOpen) {
                        TagFlowOverlay(
                            viewModel = viewModel,
                            sheetState = sheetState,
                            onStartVoiceInput = startVoiceInput
                        )
                    }
                } // end BoxWithConstraints
            } // end Scaffold
        } // end main content Box (push drawer)

        // ── 遮罩：點擊主畫面區域關閉抽屜 ──
        if (isOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { androidx.compose.ui.unit.IntOffset(drawerOffsetPx.value.toInt(), 0) }
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                        scope.launch {
                            drawerOffsetPx.animateTo(
                                0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            isDrawerOpen = false
                        }
                    }
            )
        }
    } // end Push Drawer outer Box

    // 個人資料 overlay（從底部滑入，主畫面完整保留在下方）
    AnimatedVisibility(
        visible = showProfile,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
    ) {
        ProfileScreen(
            onBack = { showProfile = false },
            onLogoutSuccess = {
                showProfile = false
                onLogout()
            },
            onUploadClick = {
                showProfile = false
                onViewHistory()
            }
        )
    }

    } // end outer Box
} // end MainScreen


@Composable
fun TagButton(isMeasuring: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isPressed by remember { mutableStateOf(false) }
    var isFlashing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
        ),
        label = "scale"
    )

    val circleColor by animateColorAsState(
        targetValue = if (isFlashing) Color(0xFFB2EBF2) else Color(0xFF80DEEA),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = if (isFlashing) 50 else 100),
        label = "circleColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(300.dp)
    ) {
        // Blue circle - only shown when measuring, flashes on tap
        if (isMeasuring) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(circleColor, CircleShape)
            )
        }

        // Main image - switches based on recording state
        Image(
            painter = painterResource(
                id = when {
                    !isMeasuring          -> R.drawable.btn_notready
                    isPressed || isFlashing -> R.drawable.btn_pressed
                    else                  -> R.drawable.btn_standby
                }
            ),
            contentDescription = "Tag",
            modifier = Modifier
                .size(290.dp)
                .scale(scale)
                .pointerInput(isMeasuring) {
                    if (isMeasuring) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = {
                                scope.launch {
                                    if (vibrator.hasVibrator()) {
                                        val pattern = longArrayOf(0, 200, 100, 200, 100, 400)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator.vibrate(pattern, -1)
                                        }
                                    }

                                    // Triple flash - only the blue circle
                                    isFlashing = true
                                    kotlinx.coroutines.delay(200)
                                    isFlashing = false
                                    kotlinx.coroutines.delay(100)

                                    isFlashing = true
                                    kotlinx.coroutines.delay(200)
                                    isFlashing = false
                                    kotlinx.coroutines.delay(100)

                                    isFlashing = true
                                    kotlinx.coroutines.delay(400)
                                    isFlashing = false

                                    onClick()
                                }
                            }
                        )
                    }
                },
            contentScale = ContentScale.Fit
        )
    }
}

private val TriangleShape = object : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}
