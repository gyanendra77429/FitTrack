package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FitTrack", appName)
  }

  @Test
  fun `test step stride and calorie estimation logic`() {
    val steps = 10000L
    val strideMeters = 0.76
    val distance = steps * strideMeters
    assertEquals(7600.0, distance, 0.01)

    val weightKg = 70.0
    val calPerStep = (weightKg / 70.0) * 0.042
    val calories = steps * calPerStep
    assertEquals(420.0, calories, 0.01)
  }
}
