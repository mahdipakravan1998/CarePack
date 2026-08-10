package ir.carepack.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

internal inline fun <reified T : ViewModel> carePackViewModelFactory(
    crossinline create: () -> T,
): ViewModelProvider.Factory = viewModelFactory {
        initializer { create() }
    }
