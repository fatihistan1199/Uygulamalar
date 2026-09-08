package tr.com.gundemradari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import tr.com.gundemradari.background.BackgroundScanScheduler
import tr.com.gundemradari.notifications.CriticalNotificationManager
import tr.com.gundemradari.ui.GundemApp
import tr.com.gundemradari.ui.GundemTheme

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)

        BackgroundScanScheduler.ensureScheduled(applicationContext)
        CriticalNotificationManager.ensureChannel(applicationContext)

        setContent{
            GundemTheme{
                GundemApp(this@MainActivity)
            }
        }
    }
}
