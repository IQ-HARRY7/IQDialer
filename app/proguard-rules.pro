# Copyright© IQ-STUDIO 2026 (ptv limited)
# IQDialer project uses GPL3 (or later). 
# Custom R8/ProGuard rules for the release build { }

#                          _______________________________
#                             _____IQ_HARRY_07_____
# 
# **********************************************************************************************
# * | Actually nothing here, everything we planned already working. so nothing here.
# * | ~ If only in future - if the size of app increases, then it's useful. (actually that's all i know 🤡)
# * | 
# * | 
# *********************************************************************************************

# *********************************************************************************************
# DEFAULT PRO GUARD RULES ( -_- ) 🥀
# _____________________________________________________________________________________________
# Keep enough of the original source/line info that a crash in a minified
# release build can still be traced back to a real line, even though class
# and method names get obfuscated. Pairs with the mapping.txt AGP generates
# automatically at app/build/outputs/mapping/release/ -- run an obfuscated
# trace through that (via retrace) to get real names back too.-keepattributes SourceFile,LineNumberTable-renamesourcefileattribute SourceFile 

## *** (yeah, yeah all those shits) 😒 i wonder why there's no Default method for this ~


##***  (No Use - it's information)
# Nothing else in this file on purpose. This app doesn't use reflection,
# annotation processing, or a serialization library (no Gson/Moshi/
# kotlinx.serialization) anywhere -- every data class here is either built
# directly in Kotlin code or hand-encoded to SharedPreferences as delimited
# strings (AppPrefs), never read back by field name, so R8 can safely
# rename/shrink all of it. Compose, Media3, and the rest of the AndroidX
# dependencies already ship their own consumer rules inside their AARs,
# which AGP merges in automatically once minification is on -- copying
# generic template rules for them here would just be dead weight nobody
# reads. Manifest-declared components (every Activity/Service/Receiver in
# this project) are kept by AGP's own default rules without anything added
# here either. ***(Isn't it obvious? 🙄😐)
#
# If a reflection-based library gets added later, that's when a real rule
# belongs here, e.g.:
# -keep class com.iqstudio.dialer.SomeClass { *; } 
# 
# I'll be honest, i wish such situation never appears 🙏🥀
#
#
# Warning ⚠️: you're inspecting a Professional Repo - Be careful not be fall for it 😎