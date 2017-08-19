package ns.musicplayer;

/**
 * Created by neil on 2017/8/18.
 */

class Consts {
    // 定义控制信号，分别是退出、播放/暂停、前一首、后一首、切歌、改变进度、更新歌曲总数
    static final int CONTROL_EXIT           = 0x00;
    static final int CONTROL_PLAY_PAUSE     = 0x01;
    static final int CONTROL_PREV_SONG      = 0x02;
    static final int CONTROL_NEXT_SONG      = 0x03;
    static final int CONTROL_CUT_SONG       = 0x04;
    static final int CONTROL_TRACK          = 0x05;
    static final int CONTROL_CHANGE_MODE    = 0x06;
    static final int CONTROL_REFRESH        = 0x07;

    // 定义状态信号，分别是更新停止状态、更新播放状态、更新暂停状态、更新当前时间、更新音乐总时间、更新currentSongText内容
    static final int STATUS_STOP = 0x10;
    static final int STATUS_PLAY = 0x11;
    static final int STATUS_PAUSE = 0x12;
    static final int UPDATE_CURRENT_TIME = 0x13;
    static final int UPDATE_TOTAL_TIME = 0x14;
    static final int UPDATE_CURRENT_SONG = 0x15;

    // 定义播放模式，分别是列表循环模式、单曲循环模式、随机播放模式
    static final int MODE_CIRCLE    = 0x20;
    static final int MODE_REPEAT    = 0x21;
    static final int MODE_SHUFFLE   = 0x22;

    // 定义控制和更新两种Action，分别给Activity和Service中的Receiver接收
    static final String CONTROL_ACTION  = "CONTROL_ACTION";
    static final String UPDATE_ACTION   = "UPDATE_ACTION";
}