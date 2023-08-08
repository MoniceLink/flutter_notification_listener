package im.zoe.labs.flutter_notification_listener

import android.app.ActivityManager
import android.content.*
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.JSONMessageCodec
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.*

class FlutterNotificationListenerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
  private val TAG = "ListenerPlugin"
  private lateinit var mContext: Context
  private val METHOD_CHANNEL_NAME = "flutter_notification_listener/plugin_method"


  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    Log.i(TAG, "on attached to engine")

    mContext = flutterPluginBinding.applicationContext

    // method channel
    MethodChannel(flutterPluginBinding.binaryMessenger, METHOD_CHANNEL_NAME).setMethodCallHandler(this)

    NotificationsHandlerService.registerWith(flutterPluginBinding, mContext)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {}

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "plugin.hasPermission" -> {
        return result.success(NotificationsHandlerService.permissionGiven(mContext))
      }
      "plugin.openPermissionSettings" -> {
        return result.success(NotificationsHandlerService.openPermissionSettings(mContext))
      }
      else -> result.notImplemented()
    }
  }
}
