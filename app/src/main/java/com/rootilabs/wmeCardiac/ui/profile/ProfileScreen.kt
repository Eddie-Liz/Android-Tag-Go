package com.rootilabs.wmeCardiac.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import com.rootilabs.wmeCardiac.R
import com.rootilabs.wmeCardiac.di.ServiceLocator
import com.rootilabs.wmeCardiac.ui.theme.TagGoGreen

import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onUploadClick: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState = viewModel.uiState
    val tokenManager = ServiceLocator.tokenManager
    val rawPatientId = tokenManager.patientId ?: "---"
    val displayedPatientId = if (rawPatientId.length >= 3) {
        "xxx" + rawPatientId.substring(3)
    } else {
        rawPatientId
    }
    val vendorName = tokenManager.vendorName ?: "---"
    
    var showLogoutDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) onLogoutSuccess()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F6F9))
        ) {
            // Shared Header Background (固定 56dp，與浮動按鈕完全對齊)
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
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.btn_close_normal),
                                contentDescription = stringResource(id = R.string.back_desc),
                                tint = Color.Unspecified,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = stringResource(id = R.string.personal_profile),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = paddingValues.calculateBottomPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    ProfileInfoItem(
                        painter = painterResource(id = R.drawable.icon_id),
                        label = stringResource(id = R.string.id_number),
                        value = displayedPatientId
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    ProfileInfoItem(
                        painter = painterResource(id = R.drawable.icon_clinic),
                        label = stringResource(id = R.string.medical_clinic),
                        value = vendorName
                    )

                    Spacer(modifier = Modifier.height(60.dp))

                    Button(
                        onClick = { 
                            viewModel.checkUnsyncedData()
                            showLogoutDialog = true 
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp),
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TagGoGreen)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(stringResource(id = R.string.logout), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (uiState.error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = uiState.error!!, color = Color.Red, fontSize = 14.sp)
                    }

                    Text(
                        text = stringResource(id = R.string.version, tokenManager.appVersion),
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            unsyncedCount = uiState.unsyncedCount,
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
            },
            onUpload = {
                showLogoutDialog = false
                onUploadClick()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

@Composable
fun LogoutConfirmationDialog(
    unsyncedCount: Int = 0,
    onConfirm: () -> Unit,
    onUpload: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (unsyncedCount > 0) 
                        stringResource(id = R.string.you_still_have_data_not_uploaded_confirm_logout)
                    else 
                        stringResource(id = R.string.confirm_logout),
                    fontSize = 18.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                
                if (unsyncedCount > 0) {
                    // Alert layout with 2 buttons (removed "No")
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onUpload,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TagGoGreen)
                        ) {
                            Text(stringResource(id = R.string.upload), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text(stringResource(id = R.string.logout), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Standard Simple Confirmation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0))
                        ) {
                            Text(stringResource(id = R.string.no), color = Color.DarkGray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TagGoGreen)
                        ) {
                            Text(stringResource(id = R.string.yes), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoItem(
    painter: Painter,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, color = Color(0xFFAAAAAA), fontSize = 16.sp)
        Text(text = value, color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
