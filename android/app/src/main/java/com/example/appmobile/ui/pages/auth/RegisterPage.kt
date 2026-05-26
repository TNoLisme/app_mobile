package com.example.appmobile.ui.pages.auth

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun RegisterPage(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val authHelper = remember { FirebaseAuthHelper() }
    val db = remember { AppDatabase.getDatabase(context) }
    val userRepository = remember { UserRepository(NetworkClient.apiService, authHelper, db.userDao()) }
    val viewModel = remember { AuthViewModel(userRepository) }

    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var accountEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("male") }
    var dateOfBirth by remember { mutableStateOf("") }
    var parentPhone by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = EgDesign.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, EgDesign.cardBorder),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "EmoGarden",
                        color = EgDesign.primaryDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Tạo tài khoản",
                        color = EgDesign.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        lineHeight = 34.sp
                    )
                    Text(
                        text = "Tạo tài khoản để đồng bộ học tập và lưu tiến trình chơi của bé.",
                        color = EgDesign.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start
                    )

                    RegisterTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            errorMessage = null
                        },
                        label = "Tên hiển thị của bé",
                        placeholder = "Ví dụ: Local Player",
                        imeAction = ImeAction.Next
                    )

                    RegisterTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = "Tên đăng nhập",
                        placeholder = "Nhập tên đăng nhập",
                        imeAction = ImeAction.Next
                    )

                    RegisterTextField(
                        value = accountEmail,
                        onValueChange = {
                            accountEmail = it
                            errorMessage = null
                        },
                        label = "Email tài khoản",
                        placeholder = "example@email.com",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )

                    RegisterTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = "Mật khẩu",
                        placeholder = "Tối thiểu 8 ký tự",
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    )

                    GenderSelector(
                        value = gender,
                        onChange = {
                            gender = it
                            errorMessage = null
                        }
                    )

                    RegisterTextField(
                        value = dateOfBirth,
                        onValueChange = {
                            dateOfBirth = it.take(10)
                            errorMessage = null
                        },
                        label = "Ngày sinh (yyyy-mm-dd)",
                        placeholder = "Ví dụ: 2018-05-20",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )

                    RegisterTextField(
                        value = parentPhone,
                        onValueChange = {
                            if (it.all(Char::isDigit)) {
                                parentPhone = it.take(11)
                            }
                            errorMessage = null
                        },
                        label = "Số điện thoại phụ huynh (không bắt buộc)",
                        placeholder = "Nhập số điện thoại phụ huynh",
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    )

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = Color(0xFFFF8D8D),
                            fontSize = 13.sp
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            color = EgDesign.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val age = calculateAge(dateOfBirth)
                                val validation = validateRegister(
                                    name = displayName,
                                    username = username,
                                    email = accountEmail,
                                    password = password,
                                    dateOfBirth = dateOfBirth,
                                    age = age,
                                    phone = parentPhone
                                )
                                if (validation != null) {
                                    errorMessage = validation
                                    return@Button
                                }
                                val safeAge = age ?: run {
                                    errorMessage = "Ngày sinh chưa hợp lệ."
                                    return@Button
                                }

                                isLoading = true
                                viewModel.register(
                                    email = accountEmail.trim(),
                                    pass = password,
                                    name = displayName.trim(),
                                    age = safeAge,
                                    gender = gender,
                                    username = username.trim(),
                                    dateOfBirth = dateOfBirth.trim(),
                                    phoneNumber = parentPhone.trim().ifBlank { null }
                                ) { success, error ->
                                    isLoading = false
                                    if (success) {
                                        authHelper.auth.signOut()
                                        AppSession.clear(context)
                                        Toast.makeText(
                                            context,
                                            "Đăng ký thành công. Vui lòng đăng nhập.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onNavigateBack()
                                    } else {
                                        authHelper.auth.signOut()
                                        AppSession.clear(context)
                                        errorMessage = error ?: "Đăng ký thất bại. Vui lòng thử lại."
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EgDesign.primary)
                        ) {
                            Text(
                                text = "Đăng ký",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Đã có tài khoản? ",
                            color = EgDesign.textSecondary
                        )
                        Text(
                            text = "Đăng nhập",
                            color = EgDesign.primary,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clickable {
                                authHelper.auth.signOut()
                                AppSession.clear(context)
                                onNavigateBack()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegisterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder, color = EgDesign.textSecondary) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EgDesign.primary,
            unfocusedBorderColor = EgDesign.cardBorder,
            focusedContainerColor = EgDesign.cardSoft,
            unfocusedContainerColor = EgDesign.cardSoft,
            focusedTextColor = EgDesign.textPrimary,
            unfocusedTextColor = EgDesign.textPrimary,
            focusedLabelColor = EgDesign.primary,
            unfocusedLabelColor = EgDesign.textSecondary,
            cursorColor = EgDesign.primary
        )
    )
}

@Composable
private fun GenderSelector(value: String, onChange: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Giới tính",
            color = EgDesign.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GenderOption("Nam", value == "male") { onChange("male") }
            GenderOption("Nữ", value == "female") { onChange("female") }
            GenderOption("Khác", value == "other") { onChange("other") }
        }
    }
}

@Composable
private fun GenderOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = EgDesign.primary)
        )
        Text(
            text = label,
            color = EgDesign.textSecondary
        )
    }
}

private fun validateRegister(
    name: String,
    username: String,
    email: String,
    password: String,
    dateOfBirth: String,
    age: Int?,
    phone: String
): String? {
    if (name.isBlank() || username.isBlank() || email.isBlank() || password.isBlank() || dateOfBirth.isBlank()) {
        return "Vui lòng điền đầy đủ các mục bắt buộc."
    }
    if (!isValidEmail(email)) return "Email tài khoản chưa hợp lệ."
    if (age == null) return "Ngày sinh chưa hợp lệ (định dạng yyyy-mm-dd)."
    if (age <= 2) return "Tuổi của bé cần lớn hơn 2."
    if (password.length < 8) return "Mật khẩu cần ít nhất 8 ký tự."
    if (phone.isNotBlank() && !phone.matches(Regex("^\\d{9,11}$"))) {
        return "Số điện thoại phụ huynh chưa hợp lệ."
    }
    return null
}

private fun calculateAge(dateOfBirth: String): Int? {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val birthDate = runCatching { formatter.parse(dateOfBirth) }.getOrNull() ?: return null
    if (birthDate.after(Calendar.getInstance().time)) return null

    val birth = Calendar.getInstance().apply { time = birthDate }
    val today = Calendar.getInstance()
    var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
    if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age -= 1
    return age
}

private fun isValidEmail(value: String): Boolean {
    return value.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
}
