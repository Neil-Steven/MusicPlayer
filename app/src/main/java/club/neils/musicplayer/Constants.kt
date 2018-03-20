package club.neils.musicplayer

// 定义控制信号，分别是退出、播放/暂停、前一首、后一首、切歌、改变进度、更新歌曲总数
const val CONTROL_EXIT = 0x00
const val CONTROL_PLAY_PAUSE = 0x01
const val CONTROL_PREV_SONG = 0x02
const val CONTROL_NEXT_SONG = 0x03
const val CONTROL_CUT_SONG = 0x04
const val CONTROL_TRACK = 0x05
const val CONTROL_CHANGE_MODE = 0x06
const val CONTROL_REFRESH = 0x07

// 定义状态信号，分别是更新停止状态、更新播放状态、更新暂停状态、更新当前时间、更新音乐总时间、更新currentSongText内容
const val STATUS_STOP = 0x10
const val STATUS_PLAY = 0x11
const val STATUS_PAUSE = 0x12
const val UPDATE_CURRENT_TIME = 0x13
const val UPDATE_TOTAL_TIME = 0x14
const val UPDATE_CURRENT_SONG = 0x15

// 定义播放模式，分别是列表循环模式、单曲循环模式、随机播放模式
const val MODE_CIRCLE = 0x20
const val MODE_REPEAT = 0x21
const val MODE_SHUFFLE = 0x22

// 定义控制和更新两种Action，分别给Activity和Service中的Receiver接收
const val CONTROL_ACTION = "CONTROL_ACTION"
const val UPDATE_ACTION = "UPDATE_ACTION"