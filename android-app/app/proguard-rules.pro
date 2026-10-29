-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-keep class za.org.kasicollect.reclaimer.data.** { *; }
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
