package com.wch.flasher

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test suite for all Android instrumented tests
 * 
 * This suite runs both Espresso and Appium-based tests to provide
 * comprehensive coverage of the Android application functionality.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    MainActivityEspressoTest::class,
    MainActivityAppiumTest::class,
    NativeLibraryIntegrationTest::class
)
class AndroidTestSuite