pluginManagement {
    repositories {
        // 国内镜像只在本机生效（下载快）。
        // GitHub Actions 是海外 runner，阿里云镜像不可达会导致插件解析失败，
        // 所以 CI 环境（CI=true）直接走官方源。
        if (System.getenv("CI").isNullOrBlank()) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI").isNullOrBlank()) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "XiaodianZhangben"
include(":app")