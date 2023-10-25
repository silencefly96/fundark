object BuildVersion {
    const val compileSdkVersion = 31
    const val minSdkVersion = 19
    const val targetSdkVersion = 30
    const val versionCode = 1
    const val versionName = "1.0"
}

object Versions {
    //基本库
    const val kotlin = "1.4.21"
    const val core_ktx = "1.3.2"
    const val appcompat = "1.2.0"
    const val material = "1.2.1"
    const val constraintlayout = "2.0.1"

    //测试
    const val junit = "4.13.2"
    const val ext_junit = "1.1.2"
    const val espresso_core = "3.3.0"

    //Jetpack lifecycle
    const val lifecycle_extensions = "2.2.0"
    const val lifecycle_viewmodel_ktx = "2.4.0"
    const val lifecycle_livedata_ktx = "2.3.0-beta01"
    const val databinding = "4.1.3"

    //Jetpack Room 数据库框架
    const val room_runtime = "2.3.0"
    const val room_compiler = "2.3.0"

    //Retrofit 网络通信框架
    const val retrofit = "2.6.1"
    const val converter_gson = "2.6.1"

    //协程
    const val kotlinx_coroutines_core = "1.3.9"
    const val kotlinx_coroutines_android = "1.3.7"

    // multidex
    const val multidex = "2.0.1"

    // Glide
    const val glide = "4.11.0"
    const val glide_compiler = "4.11.0"

    // navigation
    const val navigation_fragment = "2.5.3"
    const val navigation_ui = "2.5.3"

    // volley
    const val volley = "1.2.1"
}

object Libs {
    //基本库
    const val kotlin = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}"
    const val core_ktx = "androidx.core:core-ktx:${Versions.core_ktx}"
    const val appcompat = "androidx.appcompat:appcompat:${Versions.appcompat}"
    const val material = "com.google.android.material:material:${Versions.material}"
    const val constraintlayout 
        = "androidx.constraintlayout:constraintlayout:${Versions.constraintlayout}"

    //测试
    const val junit = "junit:junit:${Versions.junit}"
    const val ext_junit = "androidx.test.ext:junit:${Versions.ext_junit}"
    const val espresso_core = "androidx.test.espresso:espresso-core:${Versions.espresso_core}"

    //Jetpack lifecycle
    const val lifecycle_extensions
        = "androidx.lifecycle:lifecycle-extensions:${Versions.lifecycle_extensions}"
    const val lifecycle_viewmodel_ktx
        = "androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycle_viewmodel_ktx}"
    const val lifecycle_livedata_ktx
        = "androidx.lifecycle:lifecycle-livedata-ktx:${Versions.lifecycle_livedata_ktx}"
    const val databinding = "com.android.databinding:viewbinding:${Versions.databinding}"

    //Jetpack Room 数据库框架
    const val room_runtime = "androidx.room:room-runtime:${Versions.room_runtime}"
    const val room_compiler = "androidx.room:room-compiler:${Versions.room_compiler}"

    //Retrofit 网络通信框架
    const val retrofit = "com.squareup.retrofit2:retrofit:${Versions.retrofit}"
    const val converter_gson = "com.squareup.retrofit2:converter-gson:${Versions.converter_gson}"

    //协程
    const val kotlinx_coroutines_core
        = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinx_coroutines_core}"
    const val kotlinx_coroutines_android
        = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.kotlinx_coroutines_android}"

    // multidex
    const val multidex = "androidx.multidex:multidex:${Versions.multidex}"

    // Glide
    const val glide = "com.github.bumptech.glide:glide:${Versions.glide}"
    const val glide_compiler = "com.github.bumptech.glide:compiler:${Versions.glide_compiler}"

    // navigation
    const val navigation_fragment
        = "androidx.navigation:navigation-fragment-ktx:${Versions.navigation_fragment}"
    const val navigation_ui = "androidx.navigation:navigation-ui-ktx:${Versions.navigation_ui}"

    // volley
    const val volley = "com.android.volley:volley:${Versions.volley}"
}
