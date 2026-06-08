package com.potato.player

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import com.potato.player.player.MainActivity

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Test
    fun testActivityLaunch() {
        try {
            val controller = Robolectric.buildActivity(MainActivity::class.java)
            controller.create().start().resume().visible()
            println("Activity launched successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
