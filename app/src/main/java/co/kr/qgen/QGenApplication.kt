package co.kr.qgen

import android.app.Application
import co.kr.qgen.core.network.networkModule
import co.kr.qgen.core.network.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class QGenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@QGenApplication)
            modules(networkModule, viewModelModule)
        }
    }
}
