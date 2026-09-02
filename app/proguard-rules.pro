# NESA release rules.
#
# Room, Hilt and Compose ship their own consumer rules; these cover what is
# specific to this app.

# Kotlin metadata is needed for reflection-free serialization of enums used in
# Room type converters.
-keepclassmembers enum * { *; }

# Keep the domain model intact: it is the contract between the scheduler, the
# database and the UI, and shrinking it buys almost nothing.
-keep class com.nesa.core.model.** { *; }
