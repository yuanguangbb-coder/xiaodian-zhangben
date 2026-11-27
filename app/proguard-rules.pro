# release 混淆规则（R8 / proguard）
#
# Room 生成的是代码而不是反射，通常不需要 keep；这里只留几条兜底，
# 避免实体字段名被裁掉影响调试日志和以后可能的序列化。
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable

-keepclassmembers class com.example.ledger.data.db.** {
    <fields>;
}

# 崩溃上报用到的栈信息
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose 运行时由官方规则处理，无需额外配置
-dontwarn org.jetbrains.annotations.**