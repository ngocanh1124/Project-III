// Created by Crt Vavros, copyright © 2022 ZeroPass. All rights reserved.
import 'dart:io';
import 'dart:typed_data';
import 'package:cccd_vietnam/dmrtd.dart';
import 'package:cccd_vietnam/extensions.dart';
import 'package:logging/logging.dart';

import 'package:flutter_nfc_kit/flutter_nfc_kit.dart';

enum NfcStatus { notSupported, disabled, enabled }

class NfcProviderError extends ComProviderError {
  NfcProviderError([String message = ""]) : super(message);
  NfcProviderError.fromException(Exception e) : super(e.toString());

  @override
  String toString() => 'NfcProviderError: $message';
}

class NfcProvider extends ComProvider {
  static final _log = Logger('nfc.provider');

  Duration timeout = const Duration(seconds: 10);

  /// [Android] Default timeout.
  NfcProvider() : super(_log);

  NFCTag? _tag;

  /// On iOS, sets NFC reader session alert message.
  Future<void> setIosAlertMessage(String message) async {
    if (Platform.isIOS) {
      return await FlutterNfcKit.setIosAlertMessage(message);
    }
  }

  static Future<NfcStatus> get nfcStatus async {
    NFCAvailability a = await FlutterNfcKit.nfcAvailability;
    switch (a) {
      case NFCAvailability.disabled:
        return NfcStatus.disabled;
      case NFCAvailability.available:
        return NfcStatus.enabled;
      default:
        return NfcStatus.notSupported;
    }
  }

  @override
  Future<void> connect(
      {Duration? timeout,
      String iosAlertMessage =
          "Hold your iPhone near the biometric Passport"}) async {
    if (isConnected()) {
      return;
    }

    try {
      _tag = await FlutterNfcKit.poll(
          timeout: timeout ?? this.timeout,
          iosAlertMessage: iosAlertMessage,
          readIso14443A: true,
          readIso14443B: true,
          readIso18092: false,
          readIso15693: false);

      // Log full tag info for debugging (type and raw fields). This helps
      // determine why some CCCD tags are not being recognized as ISO-7816.
      try {
        _log.info('Polled NFC tag: $_tag');
        // Also print to stdout so logs are visible when running the example app
        print('Polled NFC tag: $_tag');
      } catch (_) {}

      // If tag is not ISO-7816, log it. Don't silently disconnect here —
      // higher-level code can decide how to proceed. We still set _tag so
      // callers can inspect and attempt alternative handling.
      if (_tag == null) {
        _log.warning('No NFC tag was returned by poll()');
        throw NfcProviderError('No NFC tag found');
      }
      if (_tag!.type != NFCTagType.iso7816) {
        _log.warning('Detected non ISO-7816 tag type: ${_tag!.type}');
        print('Detected non ISO-7816 tag type: ${_tag!.type}');
        // Note: do not auto-disconnect here — allow caller to handle the
        // unexpected tag type. Passport operations may still work on some
        // devices even if the tag type differs.
      }
    } on Exception catch (e) {
      throw NfcProviderError.fromException(e);
    }
  }

  @override
  Future<void> disconnect(
      {String? iosAlertMessage, String? iosErrorMessage}) async {
    if (isConnected()) {
      _log.debug("Disconnecting");
      try {
        _tag = null;
        return await FlutterNfcKit.finish(
            iosAlertMessage: iosAlertMessage, iosErrorMessage: iosErrorMessage);
      } on Exception catch (e) {
        throw NfcProviderError.fromException(e);
      }
    }
  }

  @override
  bool isConnected() {
    return _tag != null;
  }

  @override
  Future<Uint8List> transceive(final Uint8List data,
      {Duration? timeout}) async {
    try {
      return await FlutterNfcKit.transceive(data,
          timeout: timeout ?? this.timeout);
    } on Exception catch (e) {
      throw NfcProviderError.fromException(e);
    }
  }
}
