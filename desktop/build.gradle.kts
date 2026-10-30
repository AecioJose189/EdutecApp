import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

// O compilador do Kotlin 2.1.0 não roda no JDK 25 (o do Gradle aqui); compila e empacota com o 21.
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation(compose.components.uiToolingPreview)
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    // Dispatchers.Main (viewModelScope) no desktop.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Mesmo cliente do Android (ver app/build.gradle.kts): supabase-kt 3.1.x por causa do Kotlin 2.1.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:functions-kt")
    // Login Google (navegador do sistema + servidor local na porta 3737) e o painel ao vivo da sessão.
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    // Membros da turma (nome e e-mail) lidos direto das tabelas, pelas políticas de RLS.
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    // QR code gerado localmente (convite e projeção da sessão).
    implementation("com.google.zxing:core:3.5.3")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")

    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "com.edutec.app.MainKt"

        // jpackage e runtime embutido do mesmo JDK 21 do toolchain.
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
            .get().metadata.installationPath.asFile.absolutePath

        // Versão dos instaladores: -PluminiVersao=1.0.1 (o workflow passa a da tag v*); MAJOR.MINOR.BUILD.
        val versao = providers.gradleProperty("luminiVersao").getOrElse("1.0.0")

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Lumini"
            packageVersion = versao
            description = "Lumini: Knowledge Tracing e Learning Analytics para o professor"
            vendor = "Lumini"
            // Saída de ./gradlew :desktop:suggestRuntimeModules (refaça ao trocar dependências)
            // + jdk.httpserver, do retorno do login Google (data/Login.kt).
            modules("java.instrument", "java.management", "java.prefs", "jdk.unsupported", "jdk.httpserver")
            linux { debPackageVersion = versao }
            windows {
                menu = true
                shortcut = true
                // Instala na pasta do usuário, sem pedir senha de administrador (computadores de laboratório).
                perUserInstall = true
                // Fixo: o Windows usa este código para reconhecer atualizações do mesmo app.
                upgradeUuid = "6f1d3c52-8a4e-4b1f-9d77-2b6a0e4f5c31"
            }
        }
    }
}

// Em desenvolvimento, ./gradlew :desktop:run usa a URL e a chave pública do local.properties
// (o mesmo do Android), a menos que as variáveis de ambiente já estejam definidas.
// (A tarefa run é criada pelo plugin do Compose depois da avaliação, por isso o matching.)
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    val propriedades = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    for (nome in listOf("SUPABASE_URL", "SUPABASE_CHAVE_PUBLICA")) {
        val valor = propriedades.getProperty(nome)?.trim()
        if (System.getenv(nome).isNullOrBlank() && !valor.isNullOrEmpty()) environment(nome, valor)
    }
}
