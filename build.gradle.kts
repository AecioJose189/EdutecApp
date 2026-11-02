plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
    // Professor no desktop (Compose Multiplatform). 1.8.2 é a última linha que roda com Kotlin 2.1.0,
    // que fica preso pelo supabase-kt 3.1.x (ver CLAUDE.md).
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
}
