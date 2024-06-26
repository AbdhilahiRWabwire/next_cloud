/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.nextcloud.ui

import android.graphics.BitmapFactory
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.espresso.intent.rule.IntentsTestRule
import com.nextcloud.test.TestActivity
import com.owncloud.android.AbstractIT
import com.owncloud.android.R
import com.owncloud.android.utils.BitmapUtils
import com.owncloud.android.utils.ScreenshotTest
import org.junit.Rule
import org.junit.Test

class BitmapIT : AbstractIT() {
    @get:Rule
    val testActivityRule = IntentsTestRule(TestActivity::class.java, true, false)

    @Test
    @ScreenshotTest
    fun roundBitmap() {
        val file = getFile("christine.jpg")
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

        val activity = testActivityRule.launchActivity(null)
        val imageView = ImageView(activity).apply {
            setImageBitmap(bitmap)
        }

        val bitmap2 = BitmapFactory.decodeFile(file.absolutePath)
        val imageView2 = ImageView(activity).apply {
            setImageBitmap(BitmapUtils.roundBitmap(bitmap2))
        }

        val linearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(context.getColor(R.color.grey_200))
        }
        linearLayout.addView(imageView, 200, 200)
        linearLayout.addView(imageView2, 200, 200)
        activity.addView(linearLayout)

        screenshot(activity)
    }

    // @Test
    // @ScreenshotTest
    // fun glideSVG() {
    //     val activity = testActivityRule.launchActivity(null)
    //     val accountProvider = UserAccountManagerImpl.fromContext(activity)
    //     val clientFactory = ClientFactoryImpl(activity)
    //
    //     val linearLayout = LinearLayout(activity).apply {
    //         orientation = LinearLayout.VERTICAL
    //         setBackgroundColor(context.getColor(R.color.grey_200))
    //     }
    //
    //     val file = getFile("christine.jpg")
    //     val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    //
    //     ImageView(activity).apply {
    //         setImageBitmap(bitmap)
    //         linearLayout.addView(this, 50, 50)
    //     }
    //
    //     downloadIcon(
    //         client.baseUri.toString() + "/apps/files/img/app.svg",
    //         activity,
    //         linearLayout,
    //         accountProvider,
    //         clientFactory
    //     )
    //
    //     downloadIcon(
    //         client.baseUri.toString() + "/core/img/actions/group.svg",
    //         activity,
    //         linearLayout,
    //         accountProvider,
    //         clientFactory
    //     )
    //
    //     activity.addView(linearLayout)
    //
    //     longSleep()
    //
    //     screenshot(activity)
    // }
    //
    // private fun downloadIcon(
    //     url: String,
    //     activity: TestActivity,
    //     linearLayout: LinearLayout,
    //     accountProvider: UserAccountManager,
    //     clientFactory: ClientFactory
    // ) {
    //     val view = ImageView(activity).apply {
    //         linearLayout.addView(this, 50, 50)
    //     }
    //     val target = object : SimpleTarget<Drawable>() {
    //         override fun onResourceReady(resource: Drawable?, glideAnimation: GlideAnimation<in Drawable>?) {
    //             view.setColorFilter(targetContext.getColor(R.color.dark), PorterDuff.Mode.SRC_ATOP)
    //             view.setImageDrawable(resource)
    //         }
    //     }
    //
    //     testActivityRule.runOnUiThread {
    //         DisplayUtils.downloadIcon(
    //             accountProvider,
    //             clientFactory,
    //             activity,
    //             url,
    //             target,
    //             R.drawable.ic_user
    //         )
    //     }
    // }
}
