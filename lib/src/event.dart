import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_notification_listener/flutter_notification_listener.dart';

/// ActionInput is the remote inputs for action
class ActionInput {
  String? label;
  String? resultKey;

  ActionInput({
    this.label,
    this.resultKey,
  });

  factory ActionInput.fromMap(Map<dynamic, dynamic> map) {
    return ActionInput(
      label: map["label"],
      resultKey: map["key"],
    );
  }
}

/// Action is the action for notification
class Action {
  String? title;
  int? id;
  int? semantic;
  List<ActionInput>? inputs;

  /// store the notifaction event
  NotificationEvent? _evt;

  Action(this._evt, {
    this.title,
    this.id,
    this.inputs,
    this.semantic,
  });

  /// create Action from map
  factory Action.fromMap(NotificationEvent? evt, Map<dynamic, dynamic> map) {
    return Action(
      evt,
      title: map["title"],
      id: map["id"],
      semantic: map["semantic"],
      inputs: ((map["inputs"] ?? []) as List<dynamic>).map((e) => ActionInput.fromMap(e)).toList(),
    );
  }

  Future<bool> tap() async {
    if (_evt == null) throw Exception("The notification is null");
    return NotificationsListener.tapNotificationAction(
      _evt!.packageName!,
      _evt!.id!,
      id!,
    );
  }

  Future<bool> postInputs(Map<String, dynamic> map) async {
    if (_evt == null) throw Exception("The notification is null");
    if (inputs == null || inputs!.length == 0) throw Exception("No inputs were provided");

    // check if we have set the data
    var hasData = false;
    for (var input in inputs!) {
      if (map[input.resultKey] != null) {
        hasData = true;
        break;
      }
    }
    if (!hasData) {
      throw Exception("You must offer data with resultKey from inputs");
    }

    return NotificationsListener.postActionInputs(
      _evt!.packageName!,
      _evt!.id!,
      id!,
      map,
    );
  }
}

/// NotificationEvent is the object converted from notification
/// Notification anatomy:
///   https://developer.android.com/guide/topics/ui/notifiers/notifications
class NotificationEvent {
  /// the notification uid
  int? uid;

  /// the notification id
  int? id;

  /// the notification create time in flutter side
  DateTime? createAt;

  /// the nofication create time in the android side
  int? timestamp;

  /// the package name of the notification
  String? packageName;

  /// the title of the notification
  String? title;

  /// the content of the notification
  String? text;

  /// DEPRECATE
  String? message;

  /// icon of the notification which setted by setSmallIcon, 
  /// at most time this is icon of the application package.
  /// So no need to set this, use a method to take from android.
  /// To display as a image use the Image.memory widget.
  /// Example:
  /// 
  /// ```
  /// Image.memory(evt.icon)
  /// ```
  // Uint8List? icon;

  /// if we have the large icon
  bool? hasLargeIcon;
  
  /// large icon of the notification which setted by setLargeIcon.
  /// To display as a image use the Image.memory widget.
  /// Example:
  /// 
  /// ```
  /// Image.memory(evt.largeIcon)
  /// ```
  Uint8List? largeIcon;

  /// if this notification can be tapped
  bool? canTap;

  /// actions of notification
  List<Action>? actions;

  /// the raw notifaction data from android
  dynamic _data;

  NotificationEvent({
    this.id,
    this.uid,
    this.createAt,
    this.packageName,
    this.title,
    this.text,
    this.message,
    this.timestamp,
    // this.icon,
    this.hasLargeIcon,
    this.largeIcon,
    this.canTap,
  });

  Map<dynamic, dynamic>? get raw => _data;

  /// Create the event from a map
  factory NotificationEvent.fromMap(Map<dynamic, dynamic> map) {
    map['hasLargeIcon'] = map['largeIcon'] != null && (map['largeIcon'] as Uint8List).isNotEmpty;
    var evt = NotificationEvent(
        createAt: DateTime.now(),
        id: map['id'],
        uid: map['uid'],
        packageName: map['package_name'],
        title: map['title'],
        text: map['text'],
        message: map["message"],
        timestamp: map["timestamp"],
        // icon: map['icon'],
        hasLargeIcon: map['hasLargeIcon'],
        largeIcon: map['largeIcon'],
        canTap: map["canTap"],
    );
  
    // set the raw data
    evt._data = map;

    // create the actions from map
    evt.actions = ((map["actions"] ?? []) as List<dynamic>).map((e) => Action.fromMap(evt, e)).toList();

    return evt;
  }

  @override
  String toString() {
    var tmp = Map<dynamic, dynamic>.from(this._data)
      ..remove('icon')
      ..remove('largeIcon');
    return json.encode(tmp).toString();
  }

  /// tap the notification return false if not exits
  Future<bool> tap() {
    if (canTap == null || canTap == false) throw Exception("The notification can not be tapped");
    return NotificationsListener.tapNotification(packageName!, id!);
  }
}

/// newEvent package level function create event from map
NotificationEvent newEvent(Map<dynamic, dynamic> data) {
  return NotificationEvent.fromMap(data);
}
