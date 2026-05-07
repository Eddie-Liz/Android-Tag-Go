package com.rootilabs.wmeCardiac.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootilabs.wmeCardiac.R
import com.rootilabs.wmeCardiac.ui.theme.TagGoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFlowOverlay(
    viewModel: MainViewModel,
    sheetState: SheetState,
    onStartVoiceInput: () -> Unit = {}
) {
    val uiState = viewModel.uiState

    ModalBottomSheet(
        onDismissRequest = { viewModel.cancelTagFlow() },
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {},
        modifier = Modifier.fillMaxWidth(),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.92f)) {
            // Unified Turquoise Header (Static)
            UnifiedTurquoiseHeader(
                step = uiState.tagFlowStep,
                onClose = { viewModel.cancelTagFlow() }
            )
            
            // Tag flow time label (Static)
            TagTimeLabel(uiState.tagTimeFormatted)
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

            // Content with original transition
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = uiState.tagFlowStep,
                    transitionSpec = {
                        val isForward = targetState > initialState
                        
                        if (isForward) {
                            // Forward: New enters from right, old exits to left
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(450, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(300)) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = tween(400, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(300))
                        } else {
                            // Backward: New enters from left, old exits to right
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = tween(450, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(300)) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(400, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(300))
                        }
                    },
                    label = "StepTransition"
                ) { targetStep ->
                    when (targetStep) {
                        TagFlowStep.SYMPTOM_SELECTION -> SymptomSelectionContent(viewModel, onStartVoiceInput)
                        TagFlowStep.EXERCISE_SELECTION -> ExerciseSelectionContent(viewModel)
                        TagFlowStep.CONFIRMATION -> ConfirmationContent(viewModel)
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedTurquoiseHeader(step: TagFlowStep, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TagGoGreen)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(id = R.string.tag_flow_header),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )



    }
}

@Composable
private fun TagTimeLabel(time: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(stringResource(id = R.string.tag_time_label, "").replace(": ", ":"))
                }
                append(" ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Thin)) {
                    append(time)
                }
            },
            fontSize = 20.sp,
            color = Color(0xFF424242)
        )
    }
}

@Composable
private fun BoxScope.SymptomSelectionContent(
    viewModel: MainViewModel,
    onStartVoiceInput: () -> Unit = {}
) {
    val uiState = viewModel.uiState

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(id = R.string.symptoms_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp, // Reduced slightly for longer languages
            lineHeight = 34.sp,
            color = Color(0xFF424242)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                symptoms.forEachIndexed { index, symptom ->
                    val isSelected = uiState.selectedSymptoms.contains(symptom.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleSymptom(symptom.id) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tint = Color(0xFF5B5B5B)
                        when (val icon = symptom.icon) {
                            is IconSource.Vector -> Icon(
                                imageVector = icon.imageVector,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(40.dp).padding(6.dp) // Added padding to scale down vector
                            )
                            is IconSource.Resource -> Icon(
                                painter = painterResource(id = icon.resId),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Text(
                            text = stringResource(id = symptom.labelResId),
                            fontSize = 18.sp, // Reduced (20 -> 18) for better fit
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5B5B5B),
                            modifier = Modifier.weight(1f),
                            softWrap = true, // Ensure multi-line support
                            maxLines = 3    // Allow up to 3 lines
                        )

                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.icon_check),
                                contentDescription = "Selected",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    if (index < symptoms.lastIndex) {
                        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    }
                }
                HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
            }

            // Other symptom
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.icon_others),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.symptom_others),
                    fontSize = 16.sp, // Reduced (17 -> 16)
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5B5B5B),
                    modifier = Modifier.weight(1f) // Removed maxLines=1 to allow full display
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(100.dp)
                    .border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
            ) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides Color(0xFF424242)
                ) {
                    BasicTextField(
                        value = uiState.otherSymptom,
                        onValueChange = { viewModel.setOtherSymptom(it) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.White)
                            .padding(horizontal = 12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 16.sp,
                            color = Color(0xFF424242)
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF424242)),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                                innerTextField()
                            }
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(60.dp)
                        .background(Color(0xFF616161))
                        .clickable { onStartVoiceInput() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.icon_speak),
                        contentDescription = stringResource(id = R.string.voice_input),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Bottom buttons
        TagFlowBottomButtons(
            leftText = stringResource(id = R.string.cancel_tag),
            rightText = stringResource(id = R.string.next_step),
            rightEnabled = uiState.selectedSymptoms.isNotEmpty() || uiState.otherSymptom.isNotBlank(),
            onLeft = { viewModel.cancelTagFlow() },
            onRight = { viewModel.goToExerciseSelection() }
        )
    }
}

@Composable
private fun BoxScope.ExerciseSelectionContent(viewModel: MainViewModel) {
    val uiState = viewModel.uiState

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(id = R.string.intensity_title),
            modifier = Modifier.padding(16.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            color = Color(0xFF424242)
        )
        HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                exercises.forEachIndexed { index, exercise ->
                    val isSelected = uiState.selectedExercise == exercise.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectExercise(exercise.id) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tint = Color(0xFF5B5B5B)
                        when (val icon = exercise.icon) {
                            is IconSource.Vector -> Icon(
                                imageVector = icon.imageVector,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(40.dp)
                            )
                            is IconSource.Resource -> Icon(
                                painter = painterResource(id = icon.resId),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = exercise.labelResId), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5B5B5B))
                            Text(stringResource(id = exercise.descResId), fontSize = 13.sp, color = Color(0xFF616161))
                        }
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.icon_check),
                                contentDescription = "Selected",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        TagFlowBottomButtons(
            leftText = stringResource(id = R.string.cancel_tag),
            rightText = stringResource(id = R.string.confirm),
            rightEnabled = uiState.selectedExercise >= 0,
            onLeft = { viewModel.cancelTagFlow() },
            onRight = { viewModel.goToConfirmation() }
        )
    }
}

@Composable
private fun BoxScope.ConfirmationContent(viewModel: MainViewModel) {
    val uiState = viewModel.uiState
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 捲動區域（weight(1f) 讓按鈕固定在底部）
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            // 1. Symptoms Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.goBackToSymptoms() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.symptoms_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color(0xFF424242),
                        modifier = Modifier.weight(1f)
                    )
                    Text("›", fontSize = 32.sp, color = Color(0xFFBDBDBD))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9F9F9), RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val selectedSymptoms = symptoms.filter { uiState.selectedSymptoms.contains(it.id) }
                    if (selectedSymptoms.isEmpty() && uiState.otherSymptom.isBlank()) {
                        Text(stringResource(id = R.string.no_symptoms_selected), color = Color.Gray, fontSize = 18.sp)
                    } else {
                        selectedSymptoms.forEach { symptom ->
                            ConfirmationIconRow(icon = symptom.icon, text = stringResource(id = symptom.labelResId))
                        }
                        if (uiState.otherSymptom.isNotBlank()) {
                            ConfirmationIconRow(
                                icon = IconSource.Resource(R.drawable.icon_others),
                                text = stringResource(id = R.string.other_symptom, uiState.otherSymptom)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.goBackToExercise() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.intensity_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color(0xFF424242),
                        modifier = Modifier.weight(1f)
                    )
                    Text("›", fontSize = 32.sp, color = Color(0xFFBDBDBD))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9F9F9), RoundedCornerShape(4.dp))
                        .padding(12.dp)
                ) {
                    val exercise = exercises.find { it.id == uiState.selectedExercise }
                    if (exercise == null) {
                        Text(stringResource(id = R.string.no_intensity_selected), color = Color.Gray, fontSize = 18.sp)
                    } else {
                        ConfirmationIconRow(icon = exercise.icon, text = stringResource(id = exercise.labelResId))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 固定在底部的按鈕（與症狀/強度頁面位置一致）
        TagFlowBottomButtons(
            leftText = stringResource(id = R.string.edit),
            rightText = stringResource(id = R.string.send),
            rightEnabled = true,
            onLeft = { viewModel.goBackToSymptoms() },
            onRight = { viewModel.confirmTag() }
        )
    }
}

@Composable
private fun TagFlowBottomButtons(
    leftText: String,
    rightText: String,
    rightEnabled: Boolean = true,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(8.dp) // Reduced gap for more space
    ) {
        OutlinedButton(
            onClick = onLeft,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp), // Changed fixed height to heightIn
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF757575),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), // Add padding for multi-line
            border = null
        ) {
            Text(
                leftText, 
                fontSize = 16.sp, // Further reduced (18 -> 16) for better fit
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        Button(
            onClick = onRight,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp), // Changed fixed height to heightIn
            enabled = rightEnabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TagGoGreen,
                disabledContainerColor = Color(0xFFBDBDBD)
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Text(
                rightText, 
                fontSize = 16.sp, // Further reduced (18 -> 16)
                color = Color.White, 
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ConfirmationIconRow(icon: IconSource, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            when (icon) {
                is IconSource.Vector -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    tint = Color(0xFF757575),
                    modifier = Modifier.size(36.dp).padding(4.dp)
                )
                is IconSource.Resource -> Icon(
                    painter = painterResource(id = icon.resId),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            fontSize = 22.sp,
            color = Color(0xFF424242),
            fontWeight = FontWeight.Thin
        )
    }
}
