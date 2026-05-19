// Top-level build file where you can add configuration options common to all subprojects/modules.

buildscript {
     dependencies {
          classpath ("com.google.dagger:hilt-android-gradle-plugin:2.46.1")
          classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")
          classpath ("com.google.gms:google-services:4.4.0") // or latest

     }
}

plugins {
     alias(libs.plugins.android.application) apply false
     alias(libs.plugins.kotlin.android) apply false
     alias(libs.plugins.hilt) apply false
     alias(libs.plugins.gms) apply false

}


