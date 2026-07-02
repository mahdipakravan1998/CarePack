package ir.carepack

import android.app.Application
import ir.carepack.app.AppContainer

class CarePackApplication : Application() {

    val container: AppContainer by lazy {
        AppContainer(this)
    }

    val appContainer: AppContainer
        get() = container
}
