package com.gaba.eskukap.xposed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XC_MethodHook
import java.io.File

class HookLoader : IXposedHookLoadPackage {

    private fun readPhotoPath(): String? {
        // 1) попробуем прочитать из SharedPreferences приложения (если доступно)
        try {
            val activityThread = XposedHelpers.findClass("android.app.ActivityThread", null)
            val app = XposedHelpers.callStaticMethod(activityThread, "currentApplication") as? android.app.Application
            if (app != null) {
                val prefs = app.getSharedPreferences("fakecam", 0)
                val p = prefs.getString("photo_path", null)
                if (!p.isNullOrEmpty()) return p
            }
        } catch (e: Throwable) {
            XposedBridge.log("FakeCamera: prefs read err ${e.message}")
        }

        // 2) попробуем файл на sdcard (путь, который мы предложили)
        val sd = "/sdcard/com.gaba.eskukap/photo_path.txt"
        try {
            val f = File(sd)
            if (f.exists()) {
                val content = f.readText().trim()
                if (content.isNotEmpty()) return content
            }
        } catch (e: Throwable) {
            XposedBridge.log("FakeCamera: sd read err ${e.message}")
        }

        // 3) стандартное внутреннее file (если было сохранено через Activity.filesDir)
        val internal = "/data/user/0/com.gaba.eskukap/files/fake.jpg"
        val f2 = File(internal)
        if (f2.exists()) return internal

        return null
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Логируем загрузку модуля в приложение
        XposedBridge.log("FakeCamera: module loaded for " + lpparam.packageName)

        // Универсальная попытка: если приложение вызывает android.hardware.Camera.open() — подменим
        try {
            XposedHelpers.findAndHookMethod(
                "android.hardware.Camera",
                lpparam.classLoader,
                "open",
                XC_MethodReplacement.returnConstant(null)
            )
            XposedBridge.log("FakeCamera: hooked Camera.open()")
        } catch (e: Throwable) {
            XposedBridge.log("FakeCamera: Camera.open hook failed: ${e.message}")
        }

        // Пример подмены: если целевое приложение - PhotoPipe (пример package com.example.photopipe)
        if (lpparam.packageName == "com.example.photopipe") {
            XposedBridge.log("FakeCamera: hooking PhotoPipe internals")
            try {
                XposedHelpers.findAndHookMethod(
                    "com.example.photopipe.CameraHelper",
                    lpparam.classLoader,
                    "captureBitmap",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val path = readPhotoPath()
                                if (path != null) {
                                    val bmp: Bitmap? = BitmapFactory.decodeFile(path)
                                    if (bmp != null) {
                                        param.result = bmp
                                        XposedBridge.log("FakeCamera: substituted image for PhotoPipe from $path")
                                    } else {
                                        XposedBridge.log("FakeCamera: bmp is null for $path")
                                    }
                                } else {
                                    XposedBridge.log("FakeCamera: no path found to substitute")
                                }
                            } catch (ex: Throwable) {
                                XposedBridge.log("FakeCamera: error in afterHookedMethod ${ex.message}")
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("FakeCamera: PhotoPipe hook failed: ${e.message}")
            }
        }
    }
}
