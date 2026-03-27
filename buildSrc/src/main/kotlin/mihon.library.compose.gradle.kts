import ephyra.buildlogic.configureCompose

plugins {
    id("com.android.library")
    kotlin("android")

    id("mihon.code.lint")
}

android {
    configureCompose(this)
}
