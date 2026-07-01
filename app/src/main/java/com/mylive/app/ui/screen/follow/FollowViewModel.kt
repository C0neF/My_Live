package com.mylive.app.ui.screen.follow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mylive.app.core.site.LiveSite
import com.mylive.app.data.local.entity.FollowUserEntity
import com.mylive.app.data.repository.FollowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import com.mylive.app.data.local.entity.FollowUserTagEntity
import com.mylive.app.data.repository.SettingsRepository
import com.mylive.app.service.resolveFollowUpdateConcurrency
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject

enum class FollowGroupMode {
    STATUS, PLATFORM, TAG
}

data class FollowGroupOption(
    val id: String,
    val title: String,
    val siteId: String? = null,
    val statusId: Int? = null, // null=all, 1=直播中, 2=未开播
    val tagId: String? = null
)

@HiltViewModel
class FollowViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val settingsRepository: SettingsRepository,
    private val sites: Set<@JvmSuppressWildcards LiveSite>
) : ViewModel() {

    /**
     * Sort order is encoded in [FollowUserDao.getAll] so we just pass the flow through.
     * Order: isSpecialFollow DESC, then live 1 → unknown 0 → offline 2, then
     * liveStartTime DESC.
     */
    val follows: StateFlow<List<FollowUserEntity>> = followRepository.getAllFollows()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val groupMode = MutableStateFlow(FollowGroupMode.STATUS)
    val selectedGroupId = MutableStateFlow("all")

    val userTags: StateFlow<List<FollowUserTagEntity>> = followRepository.getAllTags()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val groupOptions: StateFlow<List<FollowGroupOption>> = combine(
        groupMode,
        follows,
        userTags
    ) { mode, followsList, tagsList ->
        val options = mutableListOf(FollowGroupOption("all", "全部"))
        when (mode) {
            FollowGroupMode.STATUS -> {
                options.add(FollowGroupOption("live", "直播中", statusId = 1))
                options.add(FollowGroupOption("offline", "未开播", statusId = 2))
            }
            FollowGroupMode.PLATFORM -> {
                val siteIds = followsList.map { it.siteId }.distinct()
                val siteSort = listOf("bilibili", "douyu", "huya", "douyin")
                val sortedSiteIds = siteIds.sortedWith(compareBy {
                    val idx = siteSort.indexOf(it)
                    if (idx >= 0) idx else 99
                })
                for (siteId in sortedSiteIds) {
                    val siteName = when (siteId) {
                        "bilibili" -> "B站"
                        "douyu" -> "斗鱼"
                        "huya" -> "虎牙"
                        "douyin" -> "抖音"
                        else -> siteId
                    }
                    options.add(FollowGroupOption("site:$siteId", siteName, siteId = siteId))
                }
            }
            FollowGroupMode.TAG -> {
                for (tag in tagsList) {
                    options.add(FollowGroupOption("tag:${tag.id}", tag.tag, tagId = tag.id))
                }
            }
        }
        options
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = listOf(FollowGroupOption("all", "全部"))
    )

    val filteredFollows: StateFlow<List<FollowUserEntity>> = combine(
        follows,
        groupMode,
        selectedGroupId,
        groupOptions,
        userTags
    ) { followsList, mode, groupId, options, tagsList ->
        val selectedOption = options.find { it.id == groupId }
        if (selectedOption == null || selectedOption.id == "all") {
            followsList
        } else {
            when (mode) {
                FollowGroupMode.STATUS -> {
                    if (selectedOption.statusId == 1) {
                        followsList.filter { it.liveStatus == 1 }
                    } else {
                        followsList.filter { it.liveStatus == 2 || it.liveStatus == 0 }
                    }
                }
                FollowGroupMode.PLATFORM -> {
                    followsList.filter { it.siteId == selectedOption.siteId }
                }
                FollowGroupMode.TAG -> {
                    val tag = tagsList.find { it.id == selectedOption.tagId }
                    if (tag != null) {
                        followsList.filter { it.id in tag.userIds }
                    } else {
                        followsList
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _updatingStatus = MutableStateFlow(false)
    val updatingStatus: StateFlow<Boolean> = _updatingStatus.asStateFlow()
    private var updateJob: Job? = null

    fun updateFollowStatus() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            _updatingStatus.value = true
            try {
                val currentFollows = followRepository.getAllFollows().first()
                val updateSemaphore = Semaphore(
                    permits = resolveFollowUpdateConcurrency(settingsRepository.updateFollowThreadCount.first())
                )
                coroutineScope {
                    currentFollows.map { follow ->
                        async(Dispatchers.IO) {
                            updateSemaphore.withPermit {
                                refreshOne(follow)
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                _updatingStatus.value = false
            }
        }
    }

    private suspend fun refreshOne(follow: FollowUserEntity) {
        try {
            val site = sites.find { it.id == follow.siteId } ?: return
            val isLive = site.getLiveStatus(follow.roomId)
            val newStatus = if (isLive) 1 else 2
            var showTime: String? = null
            if (isLive) {
                try {
                    val detail = site.getRoomDetail(follow.roomId)
                    showTime = detail.showTime
                } catch (_: Exception) {
                    // getRoomDetail 失败不影响开播状态更新
                }
            }
            if (follow.liveStatus != newStatus || (isLive && follow.showTime != showTime)) {
                followRepository.updateLiveStatus(
                    id = follow.id,
                    status = newStatus,
                    startTime = if (isLive) System.currentTimeMillis() else null,
                    showTime = if (isLive) showTime else null
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to update status for ${follow.userName}")
        }
    }

    fun removeFollow(follow: FollowUserEntity) {
        viewModelScope.launch {
            // Also clean from user tags
            val tags = followRepository.getAllTags().first()
            for (tag in tags) {
                if (follow.id in tag.userIds) {
                    followRepository.updateTag(tag.copy(userIds = tag.userIds.filter { it != follow.id }))
                }
            }
            followRepository.removeFollow(follow.id)
        }
    }

    fun toggleSpecialFollow(follow: FollowUserEntity) {
        viewModelScope.launch {
            followRepository.updateSpecialFollow(follow.id, !follow.isSpecialFollow)
        }
    }

    fun setGroupMode(mode: FollowGroupMode) {
        groupMode.value = mode
        selectedGroupId.value = "all"
    }

    fun setGroupOption(optionId: String) {
        selectedGroupId.value = optionId
    }

    fun addTag(tagName: String) {
        viewModelScope.launch {
            val newTag = FollowUserTagEntity(
                id = java.util.UUID.randomUUID().toString(),
                tag = tagName,
                userIds = emptyList()
            )
            followRepository.addTag(newTag)
        }
    }

    fun removeTag(tagId: String) {
        viewModelScope.launch {
            val tag = followRepository.getAllTags().first().find { it.id == tagId } ?: return@launch
            val followsById = followRepository.getAllFollows().first().associateBy { it.id }
            val updatedFollows = tag.userIds.mapNotNull { userId ->
                followsById[userId]?.copy(tag = "")
            }
            followRepository.addFollows(updatedFollows)
            followRepository.removeTag(tagId)
        }
    }

    fun renameTag(tagId: String, newName: String) {
        viewModelScope.launch {
            val tag = followRepository.getAllTags().first().find { it.id == tagId } ?: return@launch
            followRepository.updateTag(tag.copy(tag = newName))
            val followsById = followRepository.getAllFollows().first().associateBy { it.id }
            val updatedFollows = tag.userIds.mapNotNull { userId ->
                followsById[userId]?.copy(tag = newName)
            }
            followRepository.addFollows(updatedFollows)
        }
    }

    fun setFollowTag(follow: FollowUserEntity, targetTag: FollowUserTagEntity?) {
        viewModelScope.launch {
            val tags = followRepository.getAllTags().first()
            for (tag in tags) {
                if (follow.id in tag.userIds) {
                    followRepository.updateTag(tag.copy(userIds = tag.userIds.filter { it != follow.id }))
                }
            }
            if (targetTag != null) {
                val updatedUserIds = targetTag.userIds.toMutableList()
                if (follow.id !in updatedUserIds) {
                    updatedUserIds.add(follow.id)
                }
                followRepository.updateTag(targetTag.copy(userIds = updatedUserIds))
            }
            followRepository.addFollow(follow.copy(tag = targetTag?.tag ?: ""))
        }
    }

    suspend fun exportFollows(): String = followRepository.exportToJson()

    suspend fun importFollows(json: String) {
        followRepository.importFromJson(json)
    }
}
