import 'package:flutter/material.dart';

/// Global navigator key — cho phép điều hướng từ bất kỳ đâu (kể cả MQTT service)
/// mà không cần BuildContext.
final GlobalKey<NavigatorState> appNavigatorKey = GlobalKey<NavigatorState>();
