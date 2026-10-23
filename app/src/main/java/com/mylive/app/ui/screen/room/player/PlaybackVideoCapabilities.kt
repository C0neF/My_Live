package com.mylive.app.ui.screen.room.player

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.mylive.app.core.model.LivePlayQuality
import com.mylive.app.core.model.PlayQualityData
import com.mylive.app.core.site.douyu.douyuVideoRequirement
import timber.log.Timber

@OptIn(UnstableApi::class)
internal fun isLiveQualitySupportedByHardware(quality: LivePlayQuality): Boolean {
    if (quality.data !is PlayQualityData.Douyu) return true
    val requirement = douyuVideoRequirement(quality.quality) ?: return true
    return try {
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(MimeTypes.VIDEO_H264, false, false)
            .filter { it.hardwareAccelerated }
        decoders.isEmpty() || decoders.any {
            it.isVideoSizeAndRateSupportedV21(requirement.width, requirement.height, requirement.frameRate)
        }
    } catch (error: Exception) {
        Timber.w(error, "Unable to query live video decoder capabilities")
        true
    }
}
