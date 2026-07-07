package com.revenueaccount.app.scanner;

import com.journeyapps.barcodescanner.CaptureActivity;

/**
 * Fix for barcode scanner rotating to landscape: the default ZXing CaptureActivity
 * has no fixed orientation in its manifest entry, so "setOrientationLocked(true)"
 * simply locks it to whatever orientation the device happens to be in at launch
 * time (which can be landscape). Declaring this subclass with
 * android:screenOrientation="portrait" in the manifest forces the scanner to
 * always open in portrait mode, matching the rest of the app.
 */
public class PortraitCaptureActivity extends CaptureActivity {
}
