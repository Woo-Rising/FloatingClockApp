package com.clock.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class FloatingClockService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private val handler = Handler(Looper.getMainLooper())
    private var timeOffset = 0L
    private var updateRunnable: Runnable? = null

    // 定时点击相关
    private var timerActive = false
    private var targetTime: Long = 0
    private var targetButtonText = ""
    private var hasTriggered = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingWindow()
        startTimeSync()
    }

    private fun createFloatingWindow() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.parseColor("#CC0a0f19"))
        }

        // 标题栏
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = "⏱️ 悬浮时钟"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(12, 4, 12, 4)
            setOnClickListener { stopSelf() }
        }

        titleBar.addView(titleText)
        titleBar.addView(closeBtn)

        // 时间显示
        val mainTime = TextView(this).apply {
            id = View.generateViewId()
            tag = "mainTime"
            text = "00:00:00"
            textSize = 52f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }

        val msTime = TextView(this).apply {
            id = View.generateViewId()
            tag = "msTime"
            text = ".000"
            textSize = 24f
            setTextColor(Color.parseColor("#FFD700"))
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }

        val dateText = TextView(this).apply {
            id = View.generateViewId()
            tag = "dateText"
            text = "2024-01-01 星期一"
            textSize = 14f
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }

        // 分隔线
        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
        }

        // 目标设置区域
        val targetLabel = TextView(this).apply {
            text = "🎯 点击目标（按钮文本）"
            textSize = 12f
            setTextColor(Color.parseColor("#66FFFFFF"))
            setPadding(0, 8, 0, 4)
        }

        val targetInput = EditText(this).apply {
            id = View.generateViewId()
            tag = "targetInput"
            hint = "如：立即抢购、签到"
            setHintTextColor(Color.parseColor("#44FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
            setPadding(12, 8, 12, 8)
        }

        // 时间设置
        val timeLabel = TextView(this).apply {
            text = "⏰ 定时触发时间"
            textSize = 12f
            setTextColor(Color.parseColor("#66FFFFFF"))
            setPadding(0, 8, 0, 4)
        }

        val timeInputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val hourInput = EditText(this).apply {
            id = View.generateViewId()
            tag = "hourInput"
            hint = "时"
            setHintTextColor(Color.parseColor("#44FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minuteInput = EditText(this).apply {
            id = View.generateViewId()
            tag = "minuteInput"
            hint = "分"
            setHintTextColor(Color.parseColor("#44FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val secondInput = EditText(this).apply {
            id = View.generateViewId()
            tag = "secondInput"
            hint = "秒"
            setHintTextColor(Color.parseColor("#44FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        timeInputLayout.addView(hourInput)
        timeInputLayout.addView(TextView(this).apply { text = ":"; setTextColor(Color.WHITE); textSize = 20f })
        timeInputLayout.addView(minuteInput)
        timeInputLayout.addView(TextView(this).apply { text = ":"; setTextColor(Color.WHITE); textSize = 20f })
        timeInputLayout.addView(secondInput)

        // 状态显示
        val statusText = TextView(this).apply {
            id = View.generateViewId()
            tag = "statusText"
            text = "未启动定时"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }

        // 按钮区域
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val testBtn = Button(this).apply {
            text = "🧪 测试点击"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#4428a745"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                performClick(targetInput.text.toString())
            }
        }

        val timerBtn = Button(this).apply {
            id = View.generateViewId()
            tag = "timerBtn"
            text = "▶ 启动定时"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#443366aa"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                if (timerActive) {
                    cancelTimer(statusText, it as Button)
                } else {
                    startTimer(
                        hourInput.text.toString(),
                        minuteInput.text.toString(),
                        secondInput.text.toString(),
                        targetInput.text.toString(),
                        statusText,
                        it as Button
                    )
                }
            }
        }

        buttonLayout.addView(testBtn)
        buttonLayout.addView(TextView(this).apply { layoutParams = LinearLayout.LayoutParams(8, 0) })
        buttonLayout.addView(timerBtn)

        // 添加所有视图
        container.addView(titleBar)
        container.addView(mainTime)
        container.addView(msTime)
        container.addView(dateText)
        container.addView(divider)
        container.addView(targetLabel)
        container.addView(targetInput)
        container.addView(timeLabel)
        container.addView(timeInputLayout)
        container.addView(statusText)
        container.addView(buttonLayout)

        // 悬浮窗参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        // 拖动处理
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        titleBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (e: Exception) {}
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
        floatingView = container

        // 启动时钟更新
        startClockUpdate(mainTime, msTime, dateText, statusText)
    }

    private fun startClockUpdate(
        mainTime: TextView, 
        msTime: TextView, 
        dateText: TextView,
        statusText: TextView
    ) {
        updateRunnable = object : Runnable {
            override fun run() {
                val time = getBeijingTime()
                mainTime.text = time.first
                msTime.text = time.second
                dateText.text = time.third
                
                // 检查定时
                if (timerActive && !hasTriggered) {
                    checkTimer(Calendar.getInstance().apply {
                        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                    }.timeInMillis, statusText)
                }
                
                handler.postDelayed(this, 10)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun getBeijingTime(): Triple<String, String, String> {
        val now = System.currentTimeMillis() + timeOffset
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.timeInMillis = now
        
        val time = String.format("%02d:%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND))
        
        val ms = String.format(".%03d", cal.get(Calendar.MILLISECOND))
        
        val weekDays = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
        val date = String.format("%d-%02d-%02d %s",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1])
        
        return Triple(time, ms, date)
    }

    private fun startTimer(
        hour: String, minute: String, second: String,
        target: String, statusText: TextView, btn: Button
    ) {
        val h = hour.toIntOrNull() ?: 0
        val m = minute.toIntOrNull() ?: 0
        val s = second.toIntOrNull() ?: 0
        
        if (target.isEmpty()) {
            statusText.text = "⚠️ 请输入目标按钮文本"
            return
        }
        
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, m)
        cal.set(Calendar.SECOND, s)
        cal.set(Calendar.MILLISECOND, 0)
        
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        targetTime = cal.timeInMillis
        targetButtonText = target
        timerActive = true
        hasTriggered = false
        
        btn.text = "⏸ 取消定时"
        statusText.text = "⏳ 等待: $target"
    }

    private fun cancelTimer(statusText: TextView, btn: Button) {
        timerActive = false
        hasTriggered = false
        targetButtonText = ""
        btn.text = "▶ 启动定时"
        statusText.text = "未启动定时"
    }

    private fun checkTimer(currentMs: Long, statusText: TextView) {
        if (currentMs >= targetTime) {
            hasTriggered = true
            timerActive = false
            statusText.text = "🎯 触发点击: $targetButtonText"
            performClick(targetButtonText)
        }
    }

    private fun performClick(text: String) {
        val service = AutoClickService.instance
        if (service != null) {
            service.clickByText(text)
            Toast.makeText(this, "已尝试点击: $text", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTimeSync() {
        Thread {
            try {
                val url = URL("https://worldtimeapi.org/api/timezone/Asia/Shanghai")
                val startTime = System.currentTimeMillis()
                val response = url.openConnection().getInputStream().bufferedReader().readText()
                val endTime = System.currentTimeMillis()
                
                val json = JSONObject(response)
                val serverTime = json.getLong("unixtime") * 1000
                val rtt = endTime - startTime
                timeOffset = serverTime - (System.currentTimeMillis() - rtt / 2)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "floating_clock"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "时钟服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("悬浮时钟运行中")
            .setContentText("点击可设置定时任务")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(1, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable ?: return)
        windowManager.removeView(floatingView)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}