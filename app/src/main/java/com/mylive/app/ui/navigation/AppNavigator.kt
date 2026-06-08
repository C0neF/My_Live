package com.mylive.app.ui.navigation

fun Navigator.navigateToRoom(siteId: String, roomId: String) {
    navigate(Route.LiveRoomDetail(roomId = roomId, siteId = siteId))
}
