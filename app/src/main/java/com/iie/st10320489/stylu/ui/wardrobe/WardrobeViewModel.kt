package com.iie.st10320489.stylu.ui.wardrobe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WardrobeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This the wardrobe Fragment"
    }
    val text: LiveData<String> = _text
}