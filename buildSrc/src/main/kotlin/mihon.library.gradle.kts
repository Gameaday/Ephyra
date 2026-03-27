import ephyra.buildlogic.configureAndroid
import ephyra.buildlogic.configureTest

plugins {
    id("com.android.library")
    kotlin("android")

    id("mihon.code.lint")
}

android {
    configureAndroid(this)
    configureTest()
}
