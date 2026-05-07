package com.rootilabs.wmeCardiac.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rootilabs.wmeCardiac.data.local.EventTagDbEntity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.rootilabs.wmeCardiac.R
import com.rootilabs.wmeCardiac.ui.main.exercises
import com.rootilabs.wmeCardiac.ui.main.symptoms
import com.rootilabs.wmeCardiac.ui.main.IconSource
import com.rootilabs.wmeCardiac.ui.theme.TagGoGreen

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val tags = viewModel.tags
    val isSyncing = viewModel.isSyncing
    val hasUnsynced = tags.any { it.isEdit }
    val uploadError = viewModel.uploadError

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
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
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.btn_back_normal),
                            contentDescription = stringResource(id = R.string.back_desc),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.tagging_history),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Subtitle section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF0F0F0))
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.your_tags),
                color = Color(0xFF616161),
                fontSize = 18.sp
            )
        }

        // Tag List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(tags) { tag ->
                HistoryRow(tag)
                HorizontalDivider(color = Color(0xFFE0E0E0))
            }
        }

        // Footer Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { viewModel.retryUnsyncedTags() },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .heightIn(min = 52.dp), // Use heightIn for flexibility
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasUnsynced) TagGoGreen else Color(0xFFD3D3D3),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = stringResource(id = R.string.retry_upload),
                        fontSize = 18.sp, // Reduced (24 -> 18)
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
            uploadError?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFFE53935),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun HistoryRow(tag: EventTagDbEntity) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)
    
    val exercise = exercises.find { it.id == tag.exerciseIntensity }
    val extraSymptoms = tag.eventType.mapNotNull { id ->
        if (symptoms.none { it.id == id }) {
            when (id) {
                3 -> com.rootilabs.wmeCardiac.ui.main.SymptomItem(3, R.string.symptom_headache, IconSource.Resource(R.drawable.icon_others))
                6 -> com.rootilabs.wmeCardiac.ui.main.SymptomItem(6, R.string.symptom_irregular_heartbeat, IconSource.Resource(R.drawable.icon_others))
                8 -> com.rootilabs.wmeCardiac.ui.main.SymptomItem(8, R.string.symptom_nausea, IconSource.Resource(R.drawable.icon_others))
                else -> {
                    if (id != 27) {
                        android.util.Log.d("HistoryScreen", "未知症狀 ID: $id (from eventType: ${tag.eventType})")
                    }
                    null
                }
            }
        } else {
            null
        }
    }
    
    val selectedSymptoms = symptoms.filter { tag.eventType.contains(it.id) } + extraSymptoms

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tag.tagLocalTime,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF424242),
                modifier = Modifier.weight(1f)
            )
            if (!tag.isRead) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Image(
                        painter = painterResource(id = com.rootilabs.wmeCardiac.R.drawable.icon_resend),
                        contentDescription = stringResource(id = R.string.retry_upload),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Icon(
                painter = painterResource(id = if (expanded) R.drawable.arrow_up else R.drawable.arrow_down),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
            ) {
                // 1. Symptoms Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(id = R.string.symptoms_label),
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp, // Reduced (32 -> 28)
                        lineHeight = 34.sp,
                        color = Color(0xFF424242),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF9F9F9), RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (selectedSymptoms.isEmpty() && tag.others.isNullOrBlank()) {
                            Text(stringResource(id = R.string.no_symptoms_recorded), color = Color.Gray, fontSize = 18.sp)
                        } else {
                            selectedSymptoms.forEach { symptom ->
                                IconDetailRow(icon = symptom.icon, text = stringResource(id = symptom.labelResId))
                            }
                            if (!tag.others.isNullOrBlank()) {
                                IconDetailRow(
                                    icon = IconSource.Resource(R.drawable.icon_others),
                                    text = stringResource(id = R.string.other_symptom, tag.others)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(modifier = Modifier.height(24.dp))

                // 2. Exercise Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(id = R.string.intensity_label),
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp, // Reduced (32 -> 28)
                        lineHeight = 34.sp,
                        color = Color(0xFF424242),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF9F9F9), RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        if (exercise != null) {
                            IconDetailRow(icon = exercise.icon, text = stringResource(id = exercise.labelResId))
                        } else {
                            Text(stringResource(id = R.string.no_intensity_recorded), color = Color.Gray, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IconDetailRow(icon: IconSource, text: String) {
    Text(
        text = text,
        fontSize = 22.sp,
        color = Color(0xFF5B5B5B),
        fontWeight = FontWeight.Thin,
        modifier = Modifier.fillMaxWidth()
    )
}
