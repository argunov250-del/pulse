package dev.pulse.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Точка входа приложения. Аннотация включает Hilt. */
@HiltAndroidApp
class PulseApplication : Application()
