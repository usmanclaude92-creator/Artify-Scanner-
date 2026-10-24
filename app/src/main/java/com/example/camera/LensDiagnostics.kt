package com.example.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

/** Human-readable list of the phone's cameras, for diagnosing missing ultra-wide support. */
object LensDiagnostics {

    fun equivalentFocal(ch: CameraCharacteristics): Float? {
        val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: return null
        val sensorWidth = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: return null
        return focal * 36f / sensorWidth
    }

    fun describe(context: Context): String {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val sb = StringBuilder("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n")
        val ids = try { mgr.cameraIdList } catch (e: Exception) {
            return sb.append("Cannot list cameras: ${e.message}").toString()
        }
        sb.append("Camera IDs visible to apps: ${ids.joinToString()}\n")
        for (id in ids) {
            try {
                val ch = mgr.getCameraCharacteristics(id)
                val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "back"
                    CameraCharacteristics.LENS_FACING_FRONT -> "front"
                    else -> "external"
                }
                val focal = equivalentFocal(ch)?.let { "%.0fmm".format(it) } ?: "?"
                val zoom = if (Build.VERSION.SDK_INT >= 30) ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { " zoom ${it.lower}-${it.upper}x" } ?: "" else ""
                sb.append("\n[$id] $facing, ~$focal$zoom")
                if (Build.VERSION.SDK_INT >= 28 && ch.physicalCameraIds.isNotEmpty()) {
                    sb.append("\n   logical camera; physical lenses:")
                    for (pid in ch.physicalCameraIds) {
                        val pf = try { equivalentFocal(mgr.getCameraCharacteristics(pid))?.let { "%.0fmm".format(it) } ?: "?" } catch (e: Exception) { "not readable" }
                        sb.append(" [$pid] ~$pf")
                    }
                }
            } catch (e: Exception) {
                sb.append("\n[$id] unreadable: ${e.message}")
            }
        }
        return sb.toString()
    }
}
