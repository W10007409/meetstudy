package com.wjthinkbig.meetstudy

import androidx.compose.ui.window.ComposeUIViewController
import com.wjthinkbig.meetstudy.ui.App
import platform.UIKit.UIViewController

/** Entry point for the Swift app: `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
