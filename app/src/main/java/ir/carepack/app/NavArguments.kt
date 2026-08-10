package ir.carepack.app

import androidx.navigation.NavBackStackEntry

internal fun NavBackStackEntry.requireStringArgument(
    argumentName: String,
): String = checkNotNull(arguments?.getString(argumentName))
