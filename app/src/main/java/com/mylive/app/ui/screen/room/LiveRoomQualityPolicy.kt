package com.mylive.app.ui.screen.room

internal fun selectLiveRoomPreferredQualityLevel(
    defaultQualityLevel: Int,
    cellularQualityLevel: Int,
    isCellularNetwork: Boolean
): Int = if (isCellularNetwork) cellularQualityLevel else defaultQualityLevel
