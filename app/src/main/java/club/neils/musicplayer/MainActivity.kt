package club.neils.musicplayer

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), View.OnClickListener {

    // 权限申请相关
    private var actionOnPermission: ((granted: Boolean) -> Unit)? = null
    private val myRequestPermissionCode = 12345

    private var mTotalTime = 0         // 当前音乐的总时间
    private var mCurrentTime = 0       // 实时更新当前时间

    private val handler = Handler()

    private var isTracking = false     // 判断是否在拖动SeekBar
    private var exitFlag = false       // 判断是否要退出程序

    private var singleThreadExecutor = Executors.newSingleThreadExecutor()   // 定义线程池（同时只能有一个线程运行）

    private var musicLists = mutableListOf<Music>()
    private var list = mutableListOf<Map<String, String>>()

    private var activityReceiver = MyActivityReceiver()
    private var intentService = Intent()

    private var status = STATUS_STOP        // 记录当前状态，默认为停止状态
    private var mode = MODE_CIRCLE          // 记录当前播放模式，默认为列表循环

    private var toast: Toast? = null        // 保存toast实例以防止连续弹出的Toast重叠

    private var lastExitTime: Long = 0      // 保存上次尝试返回的时间


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 自定义ToolBar
        setSupportActionBar(toolbar)
        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.refresh -> {
                    musicLists.clear()
                    list.clear()
                    initMusicList()
                    sendBroadcast(Intent(CONTROL_ACTION))
                    showToast(R.string.refresh_list_succeed)
                }

                R.id.about ->
                    AlertDialog.Builder(this)
                            .setTitle(getAppName(this) + " " + getVersionName(this))
                            .setMessage(R.string.about_message)
                            .setPositiveButton(R.string.got_it, null)
                            .show()
            }
            true
        }

        tryToInit()
    }

    override fun onDestroy() {
        unregisterReceiver(activityReceiver)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // 从其他界面返回到该界面时，重新设置时间和进度条
    override fun onResume() {
        super.onResume()
        if (status != STATUS_STOP) {
            setTotalTime()
            updateTimePosition()
        }
    }

    override fun onClick(p0: View?) {
        val intent = Intent(CONTROL_ACTION)
        when (p0!!.id) {
            R.id.playButton -> {
                intent.putExtra("control", CONTROL_PLAY_PAUSE)
            }
            R.id.prevButton -> {
                intent.putExtra("control", CONTROL_PREV_SONG)
            }
            R.id.nextButton -> {
                intent.putExtra("control", CONTROL_NEXT_SONG)
            }
            R.id.minButton -> {
                this.moveTaskToBack(true)
            }
            R.id.modeButton -> {
                when (mode) {
                    MODE_CIRCLE -> {
                        mode = MODE_REPEAT
                        modeButton.setBackgroundResource(R.drawable.repeat_button_selector)
                        showToast(String.format(getString(R.string.has_changed_to_mode), getString(R.string.repeat)))
                    }
                    MODE_REPEAT -> {
                        mode = MODE_SHUFFLE
                        modeButton.setBackgroundResource(R.drawable.shuffle_button_selector)
                        showToast(String.format(getString(R.string.has_changed_to_mode), getString(R.string.shuffle)))
                    }
                    else -> {
                        mode = MODE_CIRCLE
                        modeButton.setBackgroundResource(R.drawable.circle_button_selector)
                        showToast(String.format(getString(R.string.has_changed_to_mode), getString(R.string.circle)))
                    }
                }
                intent.putExtra("control", CONTROL_CHANGE_MODE)
                intent.putExtra("mode", mode)
            }
        }
        sendBroadcast(intent)
    }

    // 重写onKeyDown方法实现按两次返回退出程序
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 两次按键间隔不超过1秒视为退出
            if (System.currentTimeMillis() - lastExitTime > 1000) {
                showToast(R.string.press_again_to_exit)
                lastExitTime = System.currentTimeMillis()
            } else {
                exitFlag = true
                // 通知Service中线程停止
                val sendIntent = Intent(CONTROL_ACTION)
                sendIntent.putExtra("control", CONTROL_EXIT)
                sendBroadcast(sendIntent)

                stopService(intentService)
                exit()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == myRequestPermissionCode && grantResults.isNotEmpty()) {
            actionOnPermission?.invoke(grantResults[0] == 0)
        }
    }

    private fun tryToInit() {
        handlePermission(Manifest.permission.READ_EXTERNAL_STORAGE) {
            if (it) {
                init()
                refreshTimePosition()
            } else {
                AlertDialog.Builder(this)
                        .setTitle(R.string.alert)
                        .setMessage(R.string.no_permission)
                        .setPositiveButton(R.string.ok) { _, _ -> exit() }
                        .show()
            }
        }
    }

    // 初始化方法
    private fun init() {
        // 为按钮的单击事件添加监听器
        playButton.setOnClickListener(this)
        nextButton.setOnClickListener(this)
        prevButton.setOnClickListener(this)
        minButton.setOnClickListener(this)
        modeButton.setOnClickListener(this)

        seekBar.setOnSeekBarChangeListener(MySeekBarListener())

        initMusicList()
        // 定义接收UPDATE_ACTION消息的Receiver
        activityReceiver = MyActivityReceiver()
        val filter = IntentFilter()
        filter.addAction(UPDATE_ACTION)
        registerReceiver(activityReceiver, filter)

        // 注册BroadCast，监听歌曲完成时间（在MusicService里面）
        intentService = Intent(this, MusicService::class.java)
        startService(intentService)
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
            musicLists.add(Music(cursor.getString(1),
                    cursor.getString(2),
                    cursor.getInt(3),
                    cursor.getString(4),
                    cursor.getString(5)))

            // 获取歌名和艺术家名
            val map = mapOf<String, String>(Pair("name", cursor.getString(2)), Pair("artist", cursor.getString(4)))
            list.add(map)
        }
        cursor?.close()

        val adapter = SimpleAdapter(this,
                list,
                R.layout.music_list,
                arrayOf("name", "artist"),
                intArrayOf(R.id.name, R.id.artist)
        )
        musicListView.adapter = adapter
        musicListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, current, _ ->
            val intent = Intent(CONTROL_ACTION)
            intent.putExtra("control", CONTROL_CUT_SONG)
            intent.putExtra("current", current)
            sendBroadcast(intent)
        }
    }

    private fun setTotalTime() {
        totalTimeText.text = formatTime(mTotalTime)
        seekBar.progress = 0
        seekBar.max = mTotalTime
    }


    private fun updateTimePosition() {
        if (mCurrentTime > mTotalTime) {
            mCurrentTime = 0
            status = STATUS_PAUSE
        }
        seekBar.progress = mCurrentTime
        playingTimeText.text = formatTime(mCurrentTime)
    }

    private fun refreshTimePosition() {
        singleThreadExecutor.execute {
            while (true) {
                try {
                    Thread.sleep(500)      // 每0.5秒执行一次
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                // exitFlag为真时结束循环
                if (exitFlag)
                    break
                if (status != STATUS_PLAY)
                    continue
                // 在拖动进度条时停止更新SeekBar和playingTimeText
                if (!isTracking) {
                    handler.post { updateTimePosition() }
                }
            }
        }
    }

    // 弹出Toast方法
    private fun showToast(messageId: Int, duration: Int = Toast.LENGTH_SHORT) {
        showToast(getString(messageId), duration)
    }

    private fun showToast(text: String, duration: Int = Toast.LENGTH_SHORT) {
        // 防止连续弹出的Toast重叠
        if (toast != null) {
            toast!!.cancel()
            toast!!.setText(text)
            toast!!.duration = duration
        }
        toast = Toast.makeText(this, text, duration)
        toast!!.show()
    }

    private fun formatTime(second: Int): String {
        val minuteString = String.format("%d%d", second / 60 / 10, second / 60 % 10)
        val secondString = String.format("%d%d", second % 60 / 10, second % 60 % 10)
        return String.format("%s:%s", minuteString, secondString)
    }

    private fun handlePermission(permissionString: String, callback: (granted: Boolean) -> Unit) {
        actionOnPermission = null
        if (ContextCompat.checkSelfPermission(this, permissionString) == PackageManager.PERMISSION_GRANTED) {
            callback(true)
        } else {
            actionOnPermission = callback
            ActivityCompat.requestPermissions(this, arrayOf(permissionString), myRequestPermissionCode)
        }
    }

    private fun exit() {
        this.finish()
        android.os.Process.killProcess(android.os.Process.myPid()) //获取PID
        System.exit(0)
    }

    // 自定义musicSeekBar的监听器
    private inner class MySeekBarListener : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
            // 必须在非停止状态下才能响应
            if (status != STATUS_STOP) {
                playingTimeText.text = formatTime(seekBar.progress)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            isTracking = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            isTracking = false
            // 必须在非停止状态下才能响应
            if (status != STATUS_STOP) {
                // 先更新SeekBar进度
                mCurrentTime = seekBar.progress
                // 再更新播放进度
                val intent = Intent(CONTROL_ACTION)
                intent.putExtra("control", CONTROL_TRACK)
                intent.putExtra("progress", seekBar.progress * 1000)
                sendBroadcast(intent)
            } else
                seekBar.progress = 0
        }
    }

    // MainActivity的Receiver
    inner class MyActivityReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            // 获取Intent中的update消息，update代表需要更新的状态
            val update = intent.getIntExtra("update", -1)

            when (update) {
                STATUS_PLAY -> {
                    playButton.setBackgroundResource(R.drawable.pause_button_selector)
                    status = STATUS_PLAY
                }

                STATUS_PAUSE -> {
                    playButton.setBackgroundResource(R.drawable.play_button_selector)
                    status = STATUS_PAUSE
                }

                UPDATE_CURRENT_TIME -> {
                    mCurrentTime = intent.getIntExtra("currentTime", -1)
                }

                UPDATE_TOTAL_TIME -> {
                    mTotalTime = intent.getIntExtra("totalTime", -1)
                    setTotalTime()
                }

                UPDATE_CURRENT_SONG -> {
                    currentSongText.text = intent.getStringExtra("currentSong")
                }
            }
        }
    }
}