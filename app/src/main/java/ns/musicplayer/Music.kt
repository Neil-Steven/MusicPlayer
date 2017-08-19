package ns.musicplayer

/**
 * Created by neil on 2017/8/18.
 */

data class Music(
        var filename: String,
        var title: String,
        var duration: Int = 0,
        var artist: String,
        var data: String
)