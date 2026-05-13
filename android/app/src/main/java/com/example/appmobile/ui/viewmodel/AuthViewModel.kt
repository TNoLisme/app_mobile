package com.example.appmobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appmobile.data.repository.UserRepository
import kotlinx.coroutines.launch

class AuthViewModel(private val userRepository: UserRepository) : ViewModel() {

    fun register(
        email: String,
        pass: String,
        name: String,
        age: Int,
        gender: String,
        username: String? = null,
        dateOfBirth: String? = null,
        phoneNumber: String? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = userRepository.registerNewAccount(
                email = email,
                pass = pass,
                name = name,
                age = age,
                gender = gender,
                username = username,
                dateOfBirth = dateOfBirth,
                phoneNumber = phoneNumber
            )
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }
}
