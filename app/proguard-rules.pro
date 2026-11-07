# SQLCipher 通过 JNI 回调 Java 层，类名不能被混淆
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
