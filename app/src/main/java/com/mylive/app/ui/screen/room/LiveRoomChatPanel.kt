package com.mylive.app.ui.screen.room

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mylive.app.ui.theme.Icons
import com.mylive.app.ui.theme.livePlatformOnAccentColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import com.mylive.app.ui.layout.LocalAppLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mylive.app.ui.navigation.Navigator
import coil3.compose.AsyncImage
import com.mylive.app.R
import com.mylive.app.core.common.buildLiveMessageDisplaySpans
import com.mylive.app.core.common.normalizeLiveMessageDisplaySpans
import com.mylive.app.core.model.LiveMessage
import com.mylive.app.core.model.LiveMessageType
import com.mylive.app.core.model.LiveMessageSpan
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.data.repository.LiveRoomPreferences
import com.mylive.app.ui.emoji.EmojiImage
import com.mylive.app.ui.screen.room.player.LiveDanmakuSurfaceFeed
import com.mylive.app.ui.screen.room.player.LivePlaybackHostContent
import com.mylive.app.ui.screen.room.player.DanmakuConfig
import com.mylive.app.ui.screen.room.player.shouldPrepareLivePlaybackExit
import com.mylive.app.data.local.entity.playbackRoomId
import com.mylive.app.core.model.DanmakuSettingsPolicy
import com.mylive.app.ui.component.FollowRefreshProgressLine
import com.mylive.app.ui.component.followRefreshHint
import com.mylive.app.ui.component.rememberFollowRefreshEntry
import com.mylive.app.ui.screen.room.player.PlayerDanmakuActions
import com.mylive.app.ui.screen.room.player.PlayerController
import com.mylive.app.ui.screen.room.player.PlayerView
import com.mylive.app.ui.screen.room.player.PlayerViewActions
import com.mylive.app.ui.screen.room.player.PlayerViewUiState
import com.mylive.app.ui.screen.room.player.DanmakuController
import com.mylive.app.ui.screen.room.player.rememberLivePlaybackHost
import com.mylive.app.ui.screen.room.player.shouldFeedLiveDanmakuSurface
import com.mylive.app.ui.screen.room.player.shouldFeedPipDanmakuSurface
import com.mylive.app.ui.screen.room.player.playbackContainerAspectRatio
import com.mylive.app.ui.screen.settings.SettingsViewModel
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clipToBounds
import com.mylive.app.ui.screen.follow.FollowViewModel
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.core.model.LiveSuperChatMessage
import com.mylive.app.ui.navigation.Route
import com.mylive.app.ui.navigation.NavigationExitEffect
import com.mylive.app.ui.screen.room.quickaccess.QuickAccessExtraTab
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import com.mylive.app.ui.motion.AppMotion
import com.mylive.app.ui.motion.LocalAppMotionPolicy
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicLong


private const val ChatPanelMaxMessages = 200

@Composable
fun ChatPanel(
    messagesFlow: StateFlow<List<DisplayLiveMessage>>,
    noticeFlow: StateFlow<String?>,
    modifier: Modifier = Modifier,
    hostName: String = "",
    chatTextSize: Double = 14.0,
    chatTextGap: Double = 4.0,
    chatBubbleStyle: Boolean = false,
    playerResizing: Boolean = false
) {
    // Collect at the chat leaf so message snapshots do not recompose room chrome.
    val messages by messagesFlow.collectAsStateWithLifecycle()
    val notice by noticeFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var previousLastMessageId by remember { mutableStateOf<Long?>(null) }
    var autoScrollDisabled by remember { mutableStateOf(false) }
    var showLatestButton by remember { mutableStateOf(false) }
    var userTouchingChat by remember { mutableStateOf(false) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var displayMessages by remember { mutableStateOf<List<DisplayLiveMessage>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val isAtBottomNow by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index >= info.totalItemsCount - 2
        }
    }
    // Key on the last message (not messages.size): once the ring buffer saturates at its cap,
    // size stays constant and a size-keyed effect would stop firing, freezing auto-scroll in
    // busy rooms. Auto-follow is controlled by user scroll state, matching dart_simple_live:
    // once the user scrolls away, new messages show the latest button instead of pulling back.
    val followViewportHeightPx = if (userTouchingChat) 0 else viewportHeightPx
    LaunchedEffect(messages.lastOrNull()?.id, messages.size, autoScrollDisabled, userTouchingChat, followViewportHeightPx) {
        val nextDisplayMessages = mergeChatDisplayMessages(
            currentDisplay = displayMessages,
            sourceMessages = messages,
            autoScrollDisabled = autoScrollDisabled,
            maxEnabledMessages = ChatPanelMaxMessages,
            keyOf = { it.id }
        )
        displayMessages = nextDisplayMessages

        if (nextDisplayMessages.isEmpty()) {
            previousLastMessageId = null
            autoScrollDisabled = false
            showLatestButton = false
            return@LaunchedEffect
        }

        val currentLastMessageId = messages.lastOrNull()?.id
        if (!userTouchingChat && shouldAutoScrollChat(
                autoScrollDisabled = autoScrollDisabled,
                hasMessages = nextDisplayMessages.isNotEmpty()
            )
        ) {
            showLatestButton = false
            withFrameNanos { }
            listState.scrollToItem(nextDisplayMessages.lastIndex)
        } else if (shouldShowLatestChatButton(
                previousLastMessageId = previousLastMessageId,
                currentLastMessageId = currentLastMessageId,
                autoScrollDisabled = autoScrollDisabled
            )
        ) {
            showLatestButton = true
        }
        previousLastMessageId = currentLastMessageId
    }

    LaunchedEffect(
        isAtBottomNow,
        userTouchingChat,
        playerResizing
    ) {
        // Changing the viewport during a drag is not a request to browse chat history.
        if (playerResizing) return@LaunchedEffect
        autoScrollDisabled = reduceChatAutoScrollDisabled(
            currentDisabled = autoScrollDisabled,
            isNearBottom = isAtBottomNow,
            userScrolledAwayFromBottom = userTouchingChat && !isAtBottomNow
        )
        if (!autoScrollDisabled) {
            showLatestButton = false
        }
    }

    Box(modifier = modifier.fillMaxWidth().onSizeChanged { viewportHeightPx = it.height }) {
        notice?.let { noticeText ->
            Text(
                text = noticeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        userTouchingChat = true
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        userTouchingChat = false
                    }
                },
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(chatTextGap.dp)
        ) {
            items(
                items = displayMessages,
                key = { it.id },
                contentType = { it.message.type }
            ) { item ->
                ChatMessageItem(
                    message = item.message,
                    hostName = hostName,
                    chatTextSize = chatTextSize,
                    chatBubbleStyle = chatBubbleStyle
                )
            }
        }

        AnimatedVisibility(
            visible = showLatestButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        ) {
            SmallFloatingActionButton(
                modifier = Modifier.border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    CircleShape
                ),
                onClick = {
                    coroutineScope.launch {
                        val latestMessages = messages.takeLast(ChatPanelMaxMessages)
                        autoScrollDisabled = false
                        displayMessages = latestMessages
                        showLatestButton = false
                        previousLastMessageId = messages.lastOrNull()?.id
                        if (latestMessages.isNotEmpty()) {
                            withFrameNanos { }
                            listState.scrollToItem(latestMessages.lastIndex)
                        }
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 6.dp,
                    focusedElevation = 4.dp,
                    hoveredElevation = 4.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "滚动到最新弹幕"
                )
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: LiveMessage,
    hostName: String,
    chatTextSize: Double,
    chatBubbleStyle: Boolean
) {
    when (message.type) {
        LiveMessageType.CHAT -> {
            val isSystem = message.userName == "LiveSysMessage"
            val chatSurfaceLuminance = MaterialTheme.colorScheme.surface.luminance().toDouble()
            // Light chat surfaces make yellow / lime / cyan unreadable — darken while keeping hue.
            val liveMessageColor = remember(message.color, chatSurfaceLuminance) {
                val readable = resolveReadableChatMessageColor(
                    color = message.color,
                    backgroundLuminance = chatSurfaceLuminance
                )
                val r = (readable.r / 255f).coerceIn(0f, 1f)
                val g = (readable.g / 255f).coerceIn(0f, 1f)
                val b = (readable.b / 255f).coerceIn(0f, 1f)
                Color(r, g, b)
            }
            val colorPolicy = remember(message.color) {
                resolveChatMessageColorPolicy(message.color)
            }
            val userNameColor = Color.Gray
            val messageTextColor = if (colorPolicy.applyMessageColorToText) {
                liveMessageColor
            } else {
                Color.Unspecified
            }

            val isHost = message.userName.isNotEmpty() && message.userName == hostName

            val (annotatedString, inlineContent) = remember(message, isHost, userNameColor, messageTextColor, isSystem) {
                if (isSystem) {
                    AnnotatedString(message.message) to emptyMap<String, InlineTextContent>()
                } else {
                    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
                    val builder = AnnotatedString.Builder()

                    if (isHost) {
                        builder.appendInlineContent("host_badge", "[主播]")
                        inlineContentMap["host_badge"] = InlineTextContent(
                            Placeholder(
                                width = 36.sp,
                                height = 16.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE91E63),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 4.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "主播",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = userNameColor)) {
                        append(message.userName)
                    }
                    builder.withStyle(SpanStyle(color = Color.Gray)) {
                        append("：")
                    }

                    val spans = normalizeLiveMessageDisplaySpans(
                        message.spans ?: buildLiveMessageDisplaySpans(
                            message = message.message,
                            imageUrls = message.imageUrls,
                            imageMap = message.imageMap
                        )
                    )
                    if (!spans.isNullOrEmpty()) {
                        spans.forEachIndexed { index, span ->
                            when (span) {
                                is LiveMessageSpan.Text -> {
                                    builder.withStyle(SpanStyle(color = messageTextColor)) {
                                        append(span.text)
                                    }
                                }
                                is LiveMessageSpan.Image -> {
                                    val id = "img_${index}"
                                    builder.appendInlineContent(id, "[图片]")
                                    inlineContentMap[id] = InlineTextContent(
                                        Placeholder(
                                            width = 20.sp,
                                            height = 20.sp,
                                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                        )
                                    ) {
                                        EmojiImage(
                                            url = span.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        builder.withStyle(SpanStyle(color = messageTextColor)) {
                            append(message.message)
                        }
                    }
                    builder.toAnnotatedString() to inlineContentMap
                }
            }

            val textContent = @Composable {
                if (isSystem) {
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = chatTextSize.sp,
                            color = Color.Gray
                        ),
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                    )
                } else {
                    Text(
                        text = annotatedString,
                        inlineContent = inlineContent,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = chatTextSize.sp,
                            lineHeight = (chatTextSize * 1.4).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }

            if (chatBubbleStyle && !isSystem) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        textContent()
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    textContent()
                }
            }
        }
        LiveMessageType.SUPER_CHAT -> {
            message.superChatMessage?.let { sc ->
                SuperChatCard(item = sc)
            }
        }
        LiveMessageType.ONLINE -> {
            // Online count - handled in ViewModel
        }
        LiveMessageType.GIFT -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                val gradient = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                    )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(gradient)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🎁 ", fontSize = 14.sp)
                        Text(
                            text = message.userName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = " ${message.message}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ── Super Chat Card ─────────────────────────────────────────────

