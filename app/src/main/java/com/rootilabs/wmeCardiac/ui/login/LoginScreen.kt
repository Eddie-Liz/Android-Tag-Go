package com.rootilabs.wmeCardiac.ui.login

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rootilabs.wmeCardiac.ui.theme.TagGoGreen

import androidx.compose.ui.res.stringResource
import com.rootilabs.wmeCardiac.R
import com.rootilabs.wmeCardiac.data.model.MeasurementInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState = viewModel.uiState

    var showSuccessDelay by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            showSuccessDelay = true
            kotlinx.coroutines.delay(1000)
            onLoginSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error != null && uiState.error != "NOT_MEASURING" && uiState.error != "NO_DEVICE_RECORDING") {
            kotlinx.coroutines.delay(2000)
            viewModel.clearError()
        }
    }

    if (uiState.showScanner) {
        BarcodeScannerDialog(
            onBarcodeScanned = { barcode ->
                viewModel.onBarcodeScanned(barcode)
            },
            onDismiss = {
                viewModel.onScannerDismissed()
            }
        )
    }


    if (uiState.showAlreadyLoggedInAlert) {
        LaunchedEffect(uiState.showAlreadyLoggedInAlert) {
            kotlinx.coroutines.delay(2000)
            viewModel.onDismissAlreadyLoggedInAlert()
        }
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.this_patient_has_been_logged_in),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (uiState.showDeviceSheet) {
        DeviceSelectionSheet(
            patientId = viewModel.patientId,
            measurements = uiState.measurements,
            onSelected = { viewModel.onMeasurementSelected(it) },
            onDismiss = { viewModel.onDismissDeviceSheet() }
        )
    }

    if (uiState.showServerSheet) {
        ServerSelectionSheet(
            currentServer = viewModel.selectedServer,
            onSelected = { viewModel.onServerSelected(it) },
            onDismiss = { viewModel.onDismissServerSheet() }
        )
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF616161))
            .navigationBarsPadding()
    ) {
        // Green toolbar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TagGoGreen)
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.welcome),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Avatar
            Image(
                painter = painterResource(id = R.drawable.icon_patient),
                contentDescription = "User",
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(id = R.string.login_description),
                color = Color.White,
                fontSize = 16.sp, 
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Account ID
            // Account ID
            BasicTextField(
                value = viewModel.institutionId,
                onValueChange = { viewModel.institutionId = it },
                modifier = Modifier.fillMaxWidth().height(48.dp).background(Color.White),
                enabled = !uiState.isLoading,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = Color.Black, fontWeight = FontWeight.Bold),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (viewModel.institutionId.isEmpty()) {
                            Text(stringResource(id = R.string.account_id), color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ID Number
            // ID Number
            BasicTextField(
                value = viewModel.patientId,
                onValueChange = { viewModel.patientId = it },
                modifier = Modifier.fillMaxWidth().height(48.dp).background(Color.White),
                enabled = !uiState.isLoading,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = Color.Black, fontWeight = FontWeight.Bold),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (viewModel.patientId.isEmpty()) {
                            Text(stringResource(id = R.string.id_number), color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            val arrowRotation by animateFloatAsState(
                targetValue = if (uiState.showServerSheet) 180f else 0f,
                label = "arrow"
            )

            // Server Region Selection — Now using Sheet
            Box(modifier = Modifier.fillMaxWidth()) {
                // Trigger row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color.White, RoundedCornerShape(0.dp))
                        .clickable(enabled = !uiState.isLoading) { viewModel.onShowServerSheet() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = viewModel.selectedServer.label,
                        fontSize = 17.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(id = R.string.select_server),
                        tint = Color(0xFF9E9E9E),
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(arrowRotation)
                    )
                }
            }

            if (uiState.measurements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Device ID (裝置 ID) - Read-only but clickable to show sheet if loaded
                Box(modifier = Modifier.fillMaxWidth()) {
                    val deviceDisplayText = if (uiState.selectedDeviceId != null) {
                        if (uiState.selectedDeviceIsLoggedIn) {
                            "${uiState.selectedDeviceId} (${stringResource(R.string.has_been_logged_in)})"
                        } else {
                            uiState.selectedDeviceId
                        }
                    } else {
                        ""
                    }
                    BasicTextField(
                        value = deviceDisplayText,
                        onValueChange = { },
                        modifier = Modifier.fillMaxWidth().height(48.dp).background(Color.White),
                        readOnly = true,
                        enabled = !uiState.isLoading,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = Color.Black, fontWeight = FontWeight.Bold),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (deviceDisplayText.isEmpty()) {
                                        Text(stringResource(id = R.string.device_s_id), color = Color.LightGray, fontSize = 17.sp)
                                    }
                                    innerTextField()
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF9E9E9E),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    )
                    // Overlay to catch clicks
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(enabled = uiState.measurements.isNotEmpty() && !uiState.isLoading) {
                                viewModel.onShowDeviceSheet()
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Scanner Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        // Open scanner logic
                        viewModel.onScanClicked()
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.qrcode_icon),
                    contentDescription = "Scan QR/Barcode",
                    modifier = Modifier.size(120.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color.White)
                )
            }

            Spacer(modifier = Modifier.weight(1f))



            // Loading status
            if (uiState.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TagGoGreen,
                        strokeWidth = 2.dp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // App Version Display
            val context = androidx.compose.ui.platform.LocalContext.current
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.10"
                } catch (e: Exception) {
                    "1.0.10"
                }
            }
            Text(
                text = stringResource(id = R.string.version, versionName),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Login button
            Button(
                onClick = { viewModel.login() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TagGoGreen,
                    disabledContainerColor = TagGoGreen.copy(alpha = 0.5f)
                )
            ) {
                Text(stringResource(id = R.string.login), fontSize = 18.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    // Overlays (Drawn last to be on top)
    val displayError = uiState.error ?: uiState.transientErrorMessage
    if (displayError != null) {
        val errorText = when (displayError) {
            "FIELDS_REQUIRED"    -> stringResource(R.string.please_fill_out_account_id_and_id_number)
            "FIELDS_NO_SPACES"   -> stringResource(R.string.account_id_and_id_number_does_not_allow_space_characters)
            "ALREADY_SUBSCRIBED" -> stringResource(R.string.error_already_subscribed)
            "TOKEN_FAILED", "GET_TOKEN_FAILED" -> stringResource(R.string.sign_in_failed)
            "institution is not existed", "INVALID_INSTITUTION_ID" -> stringResource(R.string.invalid_institution_id_patient)
            "INVALID_PATIENT" -> stringResource(R.string.invalid_patient)
            "MEASUREMENT_FAILED" -> stringResource(R.string.error_measurement_failed)
            "NOT_MEASURING", "NO_DEVICE_RECORDING" -> stringResource(R.string.no_device_has_started_recording)
            "UNSUPPORTED_MODE"   -> stringResource(R.string.error_unsupported_mode)
            "FATAL_ERROR"        -> stringResource(R.string.error_fatal)
            "ALREADY_LOGGED_IN"  -> stringResource(R.string.this_patient_has_been_logged_in)
            "UNKNOWN_ERROR"      -> "Unknown Error"
            else                 -> {
                when {
                    displayError.contains("institution is not existed", ignoreCase = true) -> stringResource(R.string.invalid_institution_id_patient)
                    displayError.contains("invalid_patient", ignoreCase = true) -> stringResource(R.string.invalid_patient)
                    else -> displayError
                }
            }
        }
        val isNoDeviceError = displayError == "NOT_MEASURING" || displayError == "NO_DEVICE_RECORDING"
        
        if (isNoDeviceError) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewModel.clearError() },
                properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF2F2F2),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.error),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
                        )
                        Text(
                            text = errorText,
                            fontSize = 13.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)
                        )
                        HorizontalDivider(color = Color(0xFF3C3C43).copy(alpha = 0.36f), thickness = 0.5.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable { viewModel.clearError() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "OK",
                                color = Color(0xFF007AFF),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = errorText,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Login Success Overlay
    if (showSuccessDelay) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_success),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectionSheet(
    patientId: String,
    measurements: List<MeasurementInfo>,
    onSelected: (MeasurementInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedItem by remember { mutableStateOf(measurements.firstOrNull()) }
    
    // Using a custom Dialog for the exact look of the mockup
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).background(TagGoGreen).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.select_device), 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp).border(1.dp, Color.White, CircleShape).padding(2.dp))
                    }
                }
                
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.attention_colon),
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    // Notice Text (Best of both worlds: fixed max width for consistent block shape)
                    Text(
                        text = stringResource(R.string.this_patient_id_has_duplicate_recordings),
                        color = Color.Black,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .padding(horizontal = 8.dp),
                        lineHeight = 22.sp
                    )
                }
                
                // Selection List Box (iOS-style Wheel Picker)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(horizontal = 16.dp)
                        .background(Color(0xFFF0F0F0)), // Light background
                    contentAlignment = Alignment.Center
                ) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val snappingLayout = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)
                    val scope = rememberCoroutineScope()
                    val density = androidx.compose.ui.platform.LocalDensity.current

                    // Flawless, highly reactive scroll tracking
                    LaunchedEffect(listState) {
                        snapshotFlow { 
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset 
                        }.collect { (index, offset) ->
                            val halfItemPx = with(density) { 22.dp.toPx() }
                            val centerIndex = if (offset > halfItemPx) index + 1 else index
                            
                            if (centerIndex >= 0 && centerIndex < measurements.size) {
                                val item = measurements[centerIndex]
                                if (selectedItem?.deviceId != item.deviceId) {
                                    selectedItem = item
                                }
                            }
                        }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        flingBehavior = snappingLayout,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = PaddingValues(vertical = 68.dp) // Adjusted for 180dp height
                    ) {
                        items(measurements.size) { index ->
                            val info = measurements[index]
                            val isLogged = info.isPatientSubscribed == true
                            val displayText = if (isLogged) 
                                "${info.deviceId} (${stringResource(R.string.has_been_logged_in)})" 
                                else info.deviceId ?: "Unknown"
                                
                            val isSelected = selectedItem?.deviceId == info.deviceId
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clickable { 
                                        scope.launch {
                                            listState.animateScrollToItem(index)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayText,
                                    fontSize = if (isLogged) 16.sp else 20.sp,
                                    color = if (isSelected) Color.Black else Color.Gray,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }
                    
                    // FIXED Green selection box in the middle
                    Box(
                        modifier = Modifier
                            .width(260.dp)
                            .height(40.dp)
                            .border(1.dp, TagGoGreen, RoundedCornerShape(8.dp))
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Footer buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TagGoGreen),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel).uppercase(), 
                            color = Color.White, 
                            fontSize = 18.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    val isConfirmEnabled = selectedItem != null && selectedItem?.isPatientSubscribed != true
                    
                    Button(
                        onClick = { selectedItem?.let { onSelected(it) } },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = isConfirmEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TagGoGreen,
                            disabledContainerColor = TagGoGreen.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.confirm).uppercase(), 
                            color = Color.White, 
                            fontSize = 18.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectionSheet(
    currentServer: ServerRegion,
    onSelected: (ServerRegion) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedItem by remember { mutableStateOf(currentServer) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = Color(0xFFE0E0E0),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        contentWindowInsets = { WindowInsets.navigationBars } // Correct parameter name
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            // Header with Done only
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onSelected(selectedItem) }) {
                    Text(stringResource(R.string.confirm), color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                }
            }
            
            HorizontalDivider(color = Color.LightGray)
            
            // List of servers
            Column(
                modifier = Modifier.fillMaxWidth().background(Color.White)
            ) {
                ServerRegion.values().forEach { region ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedItem = region }
                            .background(if (selectedItem == region) Color(0xFFF5F5F5) else Color.White)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = region.label,
                            fontSize = 18.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

