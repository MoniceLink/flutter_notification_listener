package im.zoe.labs.flutter_notification_listener

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.FlutterCallbackInformation
import org.json.JSONObject
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap



class NotificationsHandlerService: MethodChannel.MethodCallHandler, NotificationListenerService() {
    // notification event cache: packageName_id -> event
    private val eventsCache = HashMap<String, NotificationEvent>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        methodHandler = this
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
      when (call.method) {
          "service.tap" -> {
              // tap the notification
              Log.d(TAG, "tap the notification")
              val args = call.arguments<ArrayList<*>?>()
              val uid = args!![0]!! as String
              return result.success(tapNotification(uid))
          }
          "service.tap_action" -> {
              // tap the action
              Log.d(TAG, "tap action of notification")
              val args = call.arguments<ArrayList<*>?>()
              val uid = args!![0]!! as String
              val idx = args[1]!! as Int
              return result.success(tapNotificationAction(uid, idx))
          }
          "service.send_input" -> {
              // send the input data
              Log.d(TAG, "set the content for input and the send action")
              val args = call.arguments<ArrayList<*>?>()
              val uid = args!![0]!! as String
              val idx = args[1]!! as Int
              val data = args[2]!! as Map<*, *>
              return result.success(sendNotificationInput(uid, idx, data))
          }
          "service.get_full_notification" -> {
              val args = call.arguments<ArrayList<*>?>()
              val uid = args!![0]!! as String
              if (!eventsCache.contains(uid)) {
                  return result.error("notFound", "can't found this notification $uid", "")
              }
              return result.success(Utils.Marshaller.marshal(eventsCache[uid]?.mSbn))
          }
          else -> {
              Log.d(TAG, "unknown method ${call.method}")
              result.notImplemented()
          }
      }
    }


    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val evt = NotificationEvent(mContext, sbn)

        // store the evt to cache
        eventsCache[evt.uid] = evt
        Handler(mContext.mainLooper).post { sendEvent(evt) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val evt = NotificationEvent(mContext, sbn)
        // remove the event from cache
        eventsCache.remove(evt.uid)
        Log.d(TAG, "notification removed: ${evt.uid}")
    }

    private fun tapNotification(uid: String): Boolean {
        Log.d(TAG, "tap the notification: $uid")
        if (!eventsCache.containsKey(uid)) {
            Log.d(TAG, "notification is not exits: $uid")
            return false
        }
        val n = eventsCache[uid] ?: return false
        n.mSbn.notification.contentIntent.send()
        return true
    }

    private fun tapNotificationAction(uid: String, idx: Int): Boolean {
        Log.d(TAG, "tap the notification action: $uid @$idx")
        if (!eventsCache.containsKey(uid)) {
            Log.d(TAG, "notification is not exits: $uid")
            return false
        }
        val n = eventsCache[uid]
        if (n == null) {
            Log.e(TAG, "notification is null: $uid")
            return false
        }
        if (n.mSbn.notification.actions.size <= idx) {
            Log.e(TAG, "tap action out of range: size ${n.mSbn.notification.actions.size} index $idx")
            return false
        }

        val act = n.mSbn.notification.actions[idx]
        if (act == null) {
            Log.e(TAG, "notification $uid action $idx not exits")
            return false
        }
        act.actionIntent.send()
        return true
    }

    private fun sendNotificationInput(uid: String, idx: Int, data: Map<*, *>): Boolean {
        Log.d(TAG, "tap the notification action: $uid @$idx")
        if (!eventsCache.containsKey(uid)) {
            Log.d(TAG, "notification is not exits: $uid")
            return false
        }
        val n = eventsCache[uid]
        if (n == null) {
            Log.e(TAG, "notification is null: $uid")
            return false
        }
        if (n.mSbn.notification.actions.size <= idx) {
            Log.e(TAG, "send inputs out of range: size ${n.mSbn.notification.actions.size} index $idx")
            return false
        }

        val act = n.mSbn.notification.actions[idx]
        if (act == null) {
            Log.e(TAG, "notification $uid action $idx not exits")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            if (act.remoteInputs == null) {
                Log.e(TAG, "notification $uid action $idx remote inputs not exits")
                return false
            }

            val intent = Intent()
            val bundle = Bundle()
            act.remoteInputs.forEach {
                if (data.containsKey(it.resultKey as String)) {
                    Log.d(TAG, "add input content: ${it.resultKey} => ${data[it.resultKey]}")
                    bundle.putCharSequence(it.resultKey, data[it.resultKey] as String)
                }
            }
            RemoteInput.addResultsToIntent(act.remoteInputs, intent, bundle)
            act.actionIntent.send(mContext, 0, intent)
            Log.d(TAG, "send the input action success")
            return true
        } else {
            Log.e(TAG, "not implement :sdk < KITKAT_WATCH")
            return false
        }
    }

    companion object {
        private var eventSink: EventChannel.EventSink? = null
        private lateinit var mContext: Context
        private lateinit var methodHandler: MethodChannel.MethodCallHandler

        @JvmStatic
        private val TAG = "NotificationsListenerService"

        private const val LISTENER_METHOD_CHANNEL_NAME = "flutter_notification_listener/listener_method"
        private const val LISTENER_EVENT_CHANNEL_NAME = "flutter_notification_listener/listener_event"

        private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
        private const val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"

        fun registerWith(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding, context: Context) {
            mContext = context
            
            val eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, LISTENER_EVENT_CHANNEL_NAME)
            val channel = MethodChannel(flutterPluginBinding.binaryMessenger, LISTENER_METHOD_CHANNEL_NAME)

            channel.setMethodCallHandler(methodHandler)
            
            eventChannel.setStreamHandler(object : StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                    eventSink = sink
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            })
        }

        fun permissionGiven(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS)
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":").toTypedArray()
                for (name in names) {
                    val componentName = ComponentName.unflattenFromString(name)
                    val nameMatch = TextUtils.equals(packageName, componentName?.packageName)
                    if (nameMatch) {
                        return true
                    }
                }
            }

            return false
        }

        fun openPermissionSettings(context: Context): Boolean {
            context.startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
    }

    private fun sendEvent(evt: NotificationEvent) {
        Log.d(TAG, "send notification event: ${evt.data}")
        if (eventSink == null) {
            Log.d(TAG, "eventSink is null")
            return
        }

        try {
            eventSink!!.success(evt.data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}

