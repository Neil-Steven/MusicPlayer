package ns.musicplayer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.IBinder
import android.provider.MediaStore
import java.util.concurrent.Executors
import ns.musicplayer.Consts.*

class MusicService : Service() {

    private var musicList = mutableListOf<Music>()

    private var serviceReceiver = MyServiceReceiver()
    private var mediaPlayer = MediaPlayer()

    private var status = STATUS_STOP
    private var mode = MODE_CIRCLE

    private var singleThreadExecutor = Executors.newSingleThreadExecutor()

    private var mTotalTime = 0

    // 歌曲下标
    private var currentIndex = 0

    // 歌曲数量
    private val count: Int
        get() { return musicList.count() }


    // 获取随机数，并保证随机播放时前后两首歌不重复
    private val randomIndex: Int
        get() {
            if (count == 0 || count == 1)
                return count

            var index: Int
            while (true) {
                index = (Math.random() * count).toInt()
                if (currentIndex != index)
                    break
            }
            return index
        }

    private var exitFlag = false


    override fun onBind(intent: Intent): IBinder? {
        return null
    }


    override fun onDestroy() {
        mediaPlayer.stop()
        mediaPlayer.release()
        unregisterReceiver(serviceReceiver)
        super.onDestroy()
    }


    override fun onCreate() {
        initMusicList()

        // 设置接收UPDATE_ACTION的Receiver
        val filter = IntentFilter()
        filter.addAction(CONTROL_ACTION)
        registerReceiver(serviceReceiver, filter)

        sendTimePosition()

        super.onCreate()
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {

        val sendIntent = Intent(UPDATE_ACTION)
        sendIntent.putExtra("update", status)
        sendBroadcast(sendIntent)

        return super.onStartCommand(intent, flags, startId)
    }


    // 播放音乐
    private fun playMusic(path: String) {
        try {
            mediaPlayer.reset()                 // 重置MediaPlayer
            mediaPlayer.setDataSource(path)     // 设置要播放的文件的路径
            mediaPlayer.prepare()               // 准备播放
            mediaPlayer.start()                 // 开始播放

            // 设置mPlayer的OnCompletion监听器
            mediaPlayer.setOnCompletionListener {
                // 播放完成一首之后进行下一首
                status = STATUS_PAUSE

                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                // 当处于列表循环模式时
                if (mode == MODE_CIRCLE) {
                    currentIndex++
                    // 如果已经是列表的最后一首歌
                    if (currentIndex > count - 1)
                        currentIndex = 0
                    // 当处于随机播放模式时
                } else if (mode == MODE_SHUFFLE)
                    currentIndex = randomIndex

                playMusic(musicList[currentIndex].data)
                readyToPlay()
                status = STATUS_PLAY
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    // 初始化播放列表
    private fun initMusicList() {
        // 取得指定位置的文件设置显示到播放列表
        val music = arrayOf(MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA)

        val cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, music, null, null, null)
        while (cursor != null && cursor.moveToNext()) {
            musicList.add(Music(cursor.getString(1),
                    cursor.getString(2),
                    cursor.getInt(3),
                    cursor.getString(4),
                    cursor.getString(5)))
        }
        cursor?.close()
    }


    // 更新总时间和显示的歌名
    private fun readyToPlay() {
        // 更新总时间
        mTotalTime = mediaPlayer.duration / 1000
        val sendIntent = Intent(UPDATE_ACTION)
        sendIntent.putExtra("update", UPDATE_TOTAL_TIME)
        sendIntent.putExtra("totalTime", mTotalTime)
        sendBroadcast(sendIntent)

        // 更新显示的歌名
        val temp = resources.getString(R.string.now_playing) + musicList[currentIndex].title + " - " + musicList[currentIndex].artist
        val sendIntent2 = Intent(UPDATE_ACTION)
        sendIntent2.putExtra("update", UPDATE_CURRENT_SONG)
        sendIntent2.putExtra("currentSong", temp)
        sendBroadcast(sendIntent2)
    }


    // 更新进度条方法
    private fun sendTimePosition() {
        singleThreadExecutor.execute {
            while (true) {
                try {
                    Thread.sleep(50)    // 每0.05秒发送一次当前进度
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                // exitFlag为真时结束循环
                if (exitFlag)
                    break
                if (status != STATUS_PLAY)
                    continue

                val sendIntent = Intent(UPDATE_ACTION)
                sendIntent.putExtra("update", UPDATE_CURRENT_TIME)
                sendIntent.putExtra("currentTime", mediaPlayer.currentPosition / 1000)
                sendBroadcast(sendIntent)
            }
        }
    }


    // Service的Receiver
    private inner class MyServiceReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val control = intent.getIntExtra("control", -1)
            when (control) {

            // 用户发出退出信号后
                CONTROL_EXIT -> {
                    exitFlag = true
                }

            // 点击播放/暂停按钮后
                CONTROL_PLAY_PAUSE -> {
                    if (status == STATUS_STOP) {
                        playMusic(musicList[currentIndex].data)
                        status = STATUS_PLAY
                        readyToPlay()
                    }
                    else if (status == STATUS_PLAY) {
                        mediaPlayer.pause()
                        status = STATUS_PAUSE
                    }
                    else if (status == STATUS_PAUSE) {
                        mediaPlayer.start()
                        status = STATUS_PLAY
                    }
                }

            // 点击前一首后
                CONTROL_PREV_SONG -> {
                    status = STATUS_PAUSE
                    // 当处于列表循环或者单曲循环模式时
                    if (mode == MODE_CIRCLE || mode == MODE_REPEAT) {
                        currentIndex--
                        // 如果已经是列表的第一首歌
                        if (currentIndex < 0) {
                            currentIndex = count - 1
                        }
                    } else {
                        currentIndex = randomIndex
                    }// 当处于随机播放模式时

                    playMusic(musicList[currentIndex].data)
                    readyToPlay()
                    status = STATUS_PLAY
                }

            // 点击后一首后
                CONTROL_NEXT_SONG -> {
                    status = STATUS_PAUSE
                    // 当处于列表循环或者单曲循环模式时
                    if (mode == MODE_CIRCLE || mode == MODE_REPEAT) {
                        currentIndex++
                        // 如果已经是列表的最后一首歌
                        if (currentIndex > count - 1) {
                            currentIndex = 0
                        }
                    } else {
                        currentIndex = randomIndex
                    }// 当处于随机播放模式时

                    playMusic(musicList[currentIndex].data)
                    readyToPlay()
                    status = STATUS_PLAY
                }

            // 用户切歌后
                CONTROL_CUT_SONG -> {
                    status = STATUS_PAUSE
                    currentIndex = intent.getIntExtra("current", -1)
                    playMusic(musicList[currentIndex].data)
                    readyToPlay()
                    status = STATUS_PLAY
                }

            // 用户拖动进度条后
                CONTROL_TRACK -> {
                    val progress = intent.getIntExtra("progress", -1)
                    mediaPlayer.seekTo(progress)
                }

            // 用户切换播放模式后
                CONTROL_CHANGE_MODE -> {
                    mode = intent.getIntExtra("mode", -1)
                }

            // 刷新列表后（重新计数）
                CONTROL_REFRESH -> {
                    musicList.clear()
                    //list.clear()
                    initMusicList()
                    //count = list.size
                }
            }
            val sendIntent = Intent(UPDATE_ACTION)
            sendIntent.putExtra("update", status)
            sendBroadcast(sendIntent)
        }
    }
}
