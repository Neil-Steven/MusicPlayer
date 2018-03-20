package club.neils.musicplayer

data class Music(
        var fileName: String,
        var title: String,
        var duration: Int = 0,
        var artist: String,
        var data: String
)