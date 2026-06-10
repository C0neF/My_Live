package com.mylive.app.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.*
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Heart as PhosphorHeartFill
import com.adamglin.phosphoricons.regular.AndroidLogo

/**
 * Central icon vocabulary for the app. Backed by Lucide (consistent outline set);
 * Phosphor supplies fills/gaps Lucide doesn't cover (the filled "followed" heart and
 * the Android brand mark). Keep semantic names stable so call sites never change.
 */
object Icons {
    object Default {
        val Home: ImageVector = Lucide.House
        val Favorite: ImageVector = PhosphorIcons.Fill.PhosphorHeartFill
        val FavoriteBorder: ImageVector = Lucide.Heart
        val Category: ImageVector = Lucide.LayoutGrid
        val Chat: ImageVector = Lucide.MessageCircle
        val Music: ImageVector = Lucide.Music
        val Game: ImageVector = Lucide.Gamepad2
        val Cards: ImageVector = Lucide.Dices
        val Sport: ImageVector = Lucide.Dumbbell
        val Culture: ImageVector = Lucide.BookOpen
        val Dance: ImageVector = Lucide.Disc3
        val Role: ImageVector = Lucide.Smile
        val Movie: ImageVector = Lucide.Film
        val Trophy: ImageVector = Lucide.Trophy
        val Microphone: ImageVector = Lucide.Mic
        val Person: ImageVector = Lucide.User
        val Settings: ImageVector = Lucide.Settings
        val History: ImageVector = Lucide.History
        val Search: ImageVector = Lucide.Search
        val Refresh: ImageVector = Lucide.RefreshCw
        val Delete: ImageVector = Lucide.Trash2
        val Check: ImageVector = Lucide.Check
        val Close: ImageVector = Lucide.X
        val Add: ImageVector = Lucide.Plus
        val Edit: ImageVector = Lucide.Pencil
        val Remove: ImageVector = Lucide.Minus
        val ChevronRight: ImageVector = Lucide.ChevronRight
        val ChevronLeft: ImageVector = Lucide.ChevronLeft
        val KeyboardArrowUp: ImageVector = Lucide.ChevronUp
        val KeyboardArrowDown: ImageVector = Lucide.ChevronDown
        val KeyboardArrowRight: ImageVector = Lucide.ChevronRight
        val ArrowBack: ImageVector = Lucide.ArrowLeft
        val Lock: ImageVector = Lucide.Lock
        val LockOpen: ImageVector = Lucide.LockOpen
        val Pause: ImageVector = Lucide.Pause
        val PlayArrow: ImageVector = Lucide.Play
        val Fullscreen: ImageVector = Lucide.Maximize
        val FullscreenExit: ImageVector = Lucide.Minimize
        val Subtitles: ImageVector = Lucide.Captions
        val SubtitlesOff: ImageVector = Lucide.CaptionsOff
        val Tune: ImageVector = Lucide.SlidersHorizontal
        val Share: ImageVector = Lucide.Share2
        val ClearAll: ImageVector = Lucide.ListX
        val MoreVert: ImageVector = Lucide.EllipsisVertical
        val Link: ImageVector = Lucide.Link
        val PlayCircleOutline: ImageVector = Lucide.CirclePlay
        val PlaylistAdd: ImageVector = Lucide.ListPlus
        val ErrorOutline: ImageVector = Lucide.CircleAlert
        val Key: ImageVector = Lucide.Key
        val Wifi: ImageVector = Lucide.Wifi
        val Devices: ImageVector = Lucide.MonitorSmartphone
        val QrCode: ImageVector = Lucide.QrCode
        val Backup: ImageVector = Lucide.CloudUpload
        val CloudDownload: ImageVector = Lucide.CloudDownload
        val QrCodeScanner: ImageVector = Lucide.ScanLine
        val ContentPaste: ImageVector = Lucide.Clipboard
        val FileDownload: ImageVector = Lucide.Download
        val FileUpload: ImageVector = Lucide.Upload
        val Info: ImageVector = Lucide.Info
        val Save: ImageVector = Lucide.Save
        val Sync: ImageVector = Lucide.RefreshCw
        val Download: ImageVector = Lucide.Download
        val Upload: ImageVector = Lucide.Upload
        val Android: ImageVector = PhosphorIcons.Regular.AndroidLogo
        val Laptop: ImageVector = Lucide.Laptop
        val Phone: ImageVector = Lucide.Smartphone
        val Tv: ImageVector = Lucide.Tv
        val Timer: ImageVector = Lucide.Timer
        val ContentCopy: ImageVector = Lucide.Copy
        val Sun: ImageVector = Lucide.Sun
        val Star: ImageVector = Lucide.Star
        val Inbox: ImageVector = Lucide.Inbox
    }

    object AutoMirrored {
        object Filled {
            val ArrowBack: ImageVector = Lucide.ArrowLeft
            val VolumeUp: ImageVector = Lucide.Volume2
            val VolumeOff: ImageVector = Lucide.VolumeX
            val Send: ImageVector = Lucide.Send
        }
    }
}
