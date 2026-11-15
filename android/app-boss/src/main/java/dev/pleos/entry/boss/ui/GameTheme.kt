package dev.pleos.entry.boss.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The chrome follows the vehicle's day/night mode. Artwork keeps its own palette.
internal val Night:Color @Composable get()=MaterialTheme.colorScheme.background
internal val Panel:Color @Composable get()=MaterialTheme.colorScheme.surface
internal val Edge:Color @Composable get()=MaterialTheme.colorScheme.outlineVariant
internal val Muted:Color @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
internal val Mint:Color @Composable get()=MaterialTheme.colorScheme.primary
internal val Gold:Color @Composable get()=MaterialTheme.colorScheme.secondary
internal val Ink:Color @Composable get()=MaterialTheme.colorScheme.onSurface

@Composable internal fun GameTheme(dark:Boolean=isSystemInDarkTheme(),content:@Composable ()->Unit){
    val colors=if(dark)darkColorScheme(
        primary=Color(0xFFBCD0FF),onPrimary=Color(0xFF132B57),primaryContainer=Color(0xFF29384E),onPrimaryContainer=Color(0xFFE0E9FF),
        secondary=Color(0xFFE7C382),onSecondary=Color(0xFF30220E),
        secondaryContainer=Color(0xFF383B41),onSecondaryContainer=Color(0xFFF2F3F4),surfaceTint=Color.Transparent,
        background=Color(0xFF151619),surface=Color(0xFF222428),surfaceVariant=Color(0xFF2C2E33),
        onBackground=Color(0xFFF2F3F4),onSurface=Color(0xFFF2F3F4),onSurfaceVariant=Color(0xFFB1B5BD),
        outline=Color(0xFF777D88),outlineVariant=Color(0xFF3B3E44),error=Color(0xFFFFB4AB)
    )else lightColorScheme(
        primary=Color(0xFF2558AF),onPrimary=Color.White,primaryContainer=Color(0xFFE4EBF7),onPrimaryContainer=Color(0xFF153C78),
        secondary=Color(0xFF855714),onSecondary=Color.White,
        secondaryContainer=Color(0xFFE9EBEE),onSecondaryContainer=Color(0xFF181A1E),surfaceTint=Color.Transparent,
        background=Color(0xFFF3F4F5),surface=Color.White,surfaceVariant=Color(0xFFE9EBEE),
        onBackground=Color(0xFF181A1E),onSurface=Color(0xFF181A1E),onSurfaceVariant=Color(0xFF5D626C),
        outline=Color(0xFF7B8088),outlineVariant=Color(0xFFDDE0E4),error=Color(0xFFB3261E)
    )
    MaterialTheme(colorScheme=colors,typography=Typography(
        headlineLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=36.sp,lineHeight=44.sp,fontWeight=FontWeight.Bold),
        headlineMedium=TextStyle(fontSize=30.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold),
        titleLarge=TextStyle(fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.SemiBold),
        titleMedium=TextStyle(fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold),
        bodyLarge=TextStyle(fontSize=20.sp,lineHeight=30.sp),
        bodyMedium=TextStyle(fontSize=18.sp,lineHeight=27.sp),
        bodySmall=TextStyle(fontSize=16.sp,lineHeight=24.sp),
        labelLarge=TextStyle(fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
        labelMedium=TextStyle(fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.Medium)
    )){
        // A dark battle can be nested inside the light vehicle shell. Reset the
        // inherited content color as well as the Material palette.
        CompositionLocalProvider(LocalContentColor provides colors.onSurface,content=content)
    }
}
