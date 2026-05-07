package com.clock.floating

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val statusText = findViewById<TextView>(R.id.statusText)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val accessibilityBtn = findViewById<Button>(R.id.accessibilityBtn)
        
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                statusText.text = "需要悬浮窗权限"
                startBtn.text = "开启悬浮窗权限"
                startBtn.setOnClickListener {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivity(intent)
                }
            } else {
                statusText.text = "悬浮窗已授权 ?"
                startBtn.text = "启动悬浮窗"
                startBtn.setOnClickListener {
                    startFloatingService()
                    finish()
                }
            }
        }
        
        // 检查无障碍服务
        accessibilityBtn.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
                finish()
            }
        }
    }
    
    private fun startFloatingService() {
        val intent = Intent(this, FloatingClockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}