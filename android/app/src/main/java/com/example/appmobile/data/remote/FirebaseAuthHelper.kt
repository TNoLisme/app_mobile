package com.example.appmobile.data.remote

import com.google.firebase.auth.FirebaseAuth

class FirebaseAuthHelper {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Đăng ký - Trả về (Thành công?, UID, Lỗi?)
    fun register(email: String, pass: String, onResult: (Boolean, String?, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Trả về true và mã UID của user vừa tạo
                    val uid = task.result?.user?.uid
                    onResult(true, uid, null)
                } else {
                    // Trả về false và thông báo lỗi
                    onResult(false, null, task.exception?.message)
                }
            }
    }

    // Đăng nhập
    fun login(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onResult(true, null)
                else onResult(false, task.exception?.message)
            }
    }

    // Quên mật khẩu
    fun resetPassword(email: String, onResult: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onResult(true, null)
                else onResult(false, task.exception?.message)
            }
    }
}