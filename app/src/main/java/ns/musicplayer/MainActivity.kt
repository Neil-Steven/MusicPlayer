package ns.musicplayer

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
import ns.musicplayer.Consts.*


class MainActivity : AppCompatActivity(), View.OnClickListener {

    private val READ_EXTERNAL_STORAGE_REQUEST_CODE = 0   // 用于回调时检测的requestCode

    private var mTotalTime = 0         // 当前音乐的总时间
    private var mCurrentTime = 0       // 实时更新当前时间

    private val handler = Handler()

    private var isTracking = false     // 判断是否在拖动SeekBar
    private var exitFlag = false       // 判断是否要退出程序

    private var singleThreadExecutor = Executors.newSingleThreadExecutor()   // 定义线程池（同时只能有一个线程运行）

    private var musicLists = mutableListOf<Music>()
    private var list = mutableListOf<Map<String, String>>()

    private var activityReceiver = MyActivityReceiver()
    private var intentService = Intent(this, MusicService::class.java)

    private var status = STATUS_STOP       // 记录当前状态，默认为停止状态
    private var mode = MODE_CIRCLE         // 记录当前播放模式，默认为列表循环


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
                    val intent = Intent(CONTROL_ACTION)
                    sendBroadcast(intent)
                    showToast("列表刷新成功！")
                }

                R.id.about_me ->
                    AlertDialog.Builder(this)
                            .setTitle(resources.getString(R.string.app_name) + " " + resources.getString(R.string.version))
                            .setMessage("Written by Neil Steven with Kotlin！\n\n本demo仅用于浅尝Kotlin语法，代码逻辑层面还有较多不完善的地方，仅供参考，日后如有时间再来填坑^_^")
                            .setPositiveButton("我知道了", null)
                            .show()
            }
            true
        }

        // 进入软件前先检测是否已经被赋予了读取存储卡的权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermission()
        } else {
            init()
            refreshTimePosition()
        }
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
                if (mode == MODE_CIRCLE) {
                    mode = MODE_REPEAT
                    modeButton.setBackgroundResource(R.drawable.repeat_button_selector)
                    showToast("已切换至单曲循环模式")
                } else if (mode == MODE_REPEAT) {
                    mode = MODE_SHUFFLE
                    modeButton.setBackgroundResource(R.drawable.shuffle_button_selector)
                    showToast("已切换至随机播放模式")
                } else {
                    mode = MODE_CIRCLE
                    modeButton.setBackgroundResource(R.drawable.circle_button_selector)
                    showToast("已切换至列表循环模式")
                }

                intent.putExtra("control", CONTROL_CHANGE_MODE)
                intent.putExtra("mode", mode)
            }
        }
        sendBroadcast(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == READ_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init()
                refreshTimePosition()
            } else {
                AlertDialog.Builder(this)
                        .setTitle("提示")
                        .setMessage("您已拒绝授权，本程序即将退出。")
                        .setPositiveButton("好的") { _, _ -> exit() }
                        .show()
            }
        }
    }


    // 申请读取存储卡权限
    fun requestPermission() {
        // 如果用户已经拒绝过一次
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("您已经拒绝过本程序申请读取存储卡的权限，如果继续拒绝，本程序将依然退出。")
                    .setPositiveButton("我知道了") { _, _ ->
                        ActivityCompat.requestPermissions(this,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                READ_EXTERNAL_STORAGE_REQUEST_CODE)
                    }.show()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_EXTERNAL_STORAGE_REQUEST_CODE)
        }
    }


    // 初始化方法
    fun init() {
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


    // 弹出Toast方法
    private var toast: Toast? = null
    fun showToast(text: String, duration: Int = Toast.LENGTH_SHORT) {
        // 防止连续弹出的Toast重叠
        if (toast != null) {
            toast!!.cancel()
            toast!!.setText(text)
            toast!!.duration = duration
        }
        toast = Toast.makeText(this, text, duration)
        toast!!.show()
    }



    // 初始化播放列表
    fun initMusicList() {
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


    fun setTotalTime() {
        totalTimeText.text = formatTime(mTotalTime)
        seekBar.progress = 0
        seekBar.max = mTotalTime
    }


    fun updateTimePosition() {
        if (mCurrentTime > mTotalTime) {
            mCurrentTime = 0
            status = STATUS_PAUSE
        }
        seekBar.progress = mCurrentTime
        playingTimeText.text = formatTime(mCurrentTime)
    }


    fun refreshTimePosition() {
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


    private fun formatTime(second: Int): String {
        val minuteString = String.format("%d%d", second / 60 / 10, second / 60 % 10)
        val secondString = String.format("%d%d", second % 60 / 10, second % 60 % 10)
        return String.format("%s:%s", minuteString, secondString)
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


    // 重写onKeyDown方法实现按两次返回退出程序
    private var lastExitTime: Long = 0
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 两次按键间隔不超过1秒视为退出
            if (System.currentTimeMillis() - lastExitTime > 1000) {
                showToast("再按一次退出本程序")
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

    private fun exit() {
        this.finish()

        android.os.Process.killProcess(android.os.Process.myPid()) //获取PID
        System.exit(0)
    }
}