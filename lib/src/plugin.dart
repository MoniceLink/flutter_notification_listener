import 'dart:async';
import 'package:flutter/services.dart';

import './event.dart';

typedef EventCallbackFunc = void Function(NotificationEvent evt);

/// NotificationsListener
class AndroidNotificationsListener {
  static const CHANNELID = "flutter_notification_listener";
  static const SEND_PORT_NAME = "notifications_send_port";

  static const MethodChannel _pluginMethodChannel =
      const MethodChannel('$CHANNELID/plugin_method');

  static const MethodChannel _listenerMethodChannel =
      const MethodChannel('$CHANNELID/listener_method');

  static const EventChannel _listenerEventChannel =
      const EventChannel('$CHANNELID/listener_event');

  /// Check have permission or not
  static Future<bool?> get hasPermission async {
    return await _pluginMethodChannel.invokeMethod('plugin.hasPermission');
  }

  /// Open the settings activity
  static Future<void> openPermissionSettings() async {
    return await _pluginMethodChannel
        .invokeMethod('plugin.openPermissionSettings');
  }

  /// tap the notification
  static Future<bool> tapNotification(String uid) async {
    return await _listenerMethodChannel
            .invokeMethod<bool>('service.tap', [uid]) ??
        false;
  }

  /// tap the notification action
  /// use the index to locate the action
  static Future<bool> tapNotificationAction(String uid, int actionId) async {
    return await _listenerMethodChannel
            .invokeMethod<bool>('service.tap_action', [uid, actionId]) ??
        false;
  }

  /// set content for action's input
  /// this is useful while auto reply by notification
  static Future<bool> postActionInputs(
      String uid, int actionId, Map<String, dynamic> map) async {
    return await _listenerMethodChannel
            .invokeMethod<bool>("service.send_input", [uid, actionId, map]) ??
        false;
  }

  /// get the full notification from android
  /// with the unqiue id
  static Future<dynamic> getFullNotification(String uid) async {
    return await _listenerMethodChannel
        .invokeMethod<dynamic>("service.get_full_notification", [uid]);
  }

  /// listen the notification events
  static Stream<NotificationEvent> get onNotificationEvent {
    return _listenerEventChannel
        .receiveBroadcastStream()
        .map((dynamic event) => NotificationEvent.fromMap(event));
  }
}
