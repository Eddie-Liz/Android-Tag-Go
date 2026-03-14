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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState = viewModel.uiState

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) onLoginSuccess()
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

    if (uiState.showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDuplicateWarning() },
            containerColor = Color(0xFF2D2D2D), // Dark grey background to match mockup
            title = { 
                Text(
                    stringResource(R.string.warning), 
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ) 
            },
            text = { 
                Text(
                    stringResource(R.string.duplicate_patient_id_detected_please_verify_before_setting),
                    color = Color.White.copy(alpha = 0.8f)
                ) 
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onDismissDuplicateWarning() }) {
                    Text(
                        stringResource(id = R.string.confirm), 
                        fontWeight = FontWeight.Bold,
                        color = TagGoGreen // Green accent color for action
                    )
                }
            }
        )
    }

    if (uiState.showDeviceSheet) {
        DeviceSelectionSheet(
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

    // Transient Overlay Error (Black box with text)
    if (uiState.transientErrorMessage != null) {
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
                    text = if (uiState.transientErrorMessage == "ALREADY_LOGGED_IN") 
                        stringResource(R.string.this_patient_has_been_logged_in) else uiState.transientErrorMessage,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF616161))
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Account ID
            // Account ID
            BasicTextField(
                value = viewModel.institutionId,
                onValueChange = { viewModel.institutionId = it.trim() },
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
                onValueChange = { viewModel.patientId = it.trim() },
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

            // Error message
            if (uiState.error != null) {
                val errorText = when (uiState.error) {
                    "FIELDS_REQUIRED"    -> stringResource(R.string.error_fields_required)
                    "ALREADY_SUBSCRIBED" -> stringResource(R.string.error_already_subscribed)
                    "TOKEN_FAILED"       -> stringResource(R.string.error_token_failed)
                    "MEASUREMENT_FAILED" -> stringResource(R.string.error_measurement_failed)
                    "NOT_MEASURING"      , "NO_DEVICE_RECORDING" -> stringResource(R.string.no_device_has_started_recording)
                    "UNSUPPORTED_MODE"   -> stringResource(R.string.error_unsupported_mode)
                    "FATAL_ERROR"        -> stringResource(R.string.error_fatal)
                    "ALREADY_LOGGED_IN"  -> stringResource(R.string.this_patient_has_been_logged_in)
                    else                 -> uiState.error
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFCDD2)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorText,
                        color = Color(0xFFB71C1C),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectionSheet(
    measurements: List<MeasurementInfo>,
    onSelected: (MeasurementInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedItem by remember { mutableStateOf<MeasurementInfo?>(null) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = Color(0xFFE0E0E0), // Light grey background like iOS
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            // Header with Done only
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedItem?.let { onSelected(it) } }) {
                    Text(stringResource(R.string.confirm), color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                }
            }
            
            HorizontalDivider(color = Color.LightGray)
            
            // List of devices
            Column(
                modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.White)
            ) {
                measurements.forEach { info ->
                    val isLogged = info.isPatientSubscribed == true
                    val displayText = if (isLogged) 
                        "${info.deviceId} (${stringResource(R.string.has_been_logged_in)})" 
                        else info.deviceId ?: "Unknown"
                        
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedItem = info }
                            .background(if (selectedItem == info) Color(0xFFF5F5F5) else Color.White)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayText,
                            fontSize = 18.sp,
                            color = Color.Black
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
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
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

