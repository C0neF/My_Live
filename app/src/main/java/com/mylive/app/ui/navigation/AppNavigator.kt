package com.mylive.app.ui.navigation

fun Navigator.navigateToRoom(
    siteId: String,
    roomId: String,
    initialIsFollowing: Boolean? = null
) {
    navigate(
        Route.LiveRoomDetail(
            roomId = roomId,
            siteId = siteId,
            initialIsFollowing = initialIsFollowing
        )
    )
}
