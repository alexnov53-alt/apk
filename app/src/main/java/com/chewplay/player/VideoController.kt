package com.chewplay.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * ExoPlayer без элементов управления: видео зациклено и играет всегда,
 * а пауза/возобновление управляются состоянием «жуёт / не жуёт».
 */
class VideoController(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        playWhenReady = false
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
    }

    private var currentUri: String? = null
    private var wantPlay = false

    fun setVideo(uriString: String?) {
        if (currentUri == uriString) return
        currentUri = uriString
        if (uriString == null) {
            player.stop()
            player.clearMediaItems()
            return
        }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
        player.prepare()
        player.playWhenReady = wantPlay
    }

    fun setPlaying(play: Boolean) {
        wantPlay = play
        if (player.playWhenReady != play) {
            player.playWhenReady = play
        }
    }

    fun release() {
        player.release()
    }
}
