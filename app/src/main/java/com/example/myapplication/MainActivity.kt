package com.example.myapplication

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import timber.log.Timber


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!checkSupprtedDeviceOrFinish(this)) {
            return
        }
    }

    private fun checkSupprtedDeviceOrFinish(activity: Activity): Boolean {
        if (MIN_OPENGL_VERSION < Build.VERSION_CODES.N) {
            Timber.tag(TAG).e("Sceneform requires Android N or later")
            activity.finish()
            return false
        }
       val  openGlVersionString =
            (this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            Timber.tag(TAG).e("Sceneform requires OpenGL ES 3.0 later")
            activity.finish()
            return false
        }
        if (openGlVersionString.toDouble() >= MIN_OPENGL_VERSION) {
            supportFragmentManager.inTransaction { replace(R.id.fragmentContainer,CustomArFragment()) }
        }
        return true

    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val MIN_OPENGL_VERSION = 3.0
    }
}
