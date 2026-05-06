package com.example.appmobile.ui.pages.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.UserRepository
import com.example.appmobile.ui.theme.SoftWhite
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

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("male") }
    var dateOfBirth by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(SoftWhite, Color(0xFFE3F2FD), Color(0xFFBBDEFB))))
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onNavigateBack) { Text("← Đăng nhập") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.index_image),
                    contentDescription = "Tạo tài khoản",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp, max = 180.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Crop
                )

                Text("Tạo tài khoản", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0B3C7D))
                Text("Điền thông tin của bé để đồng bộ hồ sơ học tập.", color = Color.Gray)

                AuthTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = "Họ và tên của bé",
                    imeAction = ImeAction.Next,
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )

                AuthTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = "Tên đăng nhập",
                    imeAction = ImeAction.Next,
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )

                GenderSelector(value = gender, onChange = { gender = it })

                AuthTextField(
                    value = dateOfBirth,
                    onValueChange = {
                        dateOfBirth = it.take(10)
                        errorMessage = null
                    },
                    label = "Ngày sinh (yyyy-mm-dd)",
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )

                AuthTextField(
                    value = phone,
                    onValueChange = {
                        if (it.all(Char::isDigit)) phone = it.take(10)
                        errorMessage = null
                    },
                    label = "Số điện thoại phụ huynh",
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )

                AuthTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = "Email phụ huynh",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Mật khẩu") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                errorMessage?.let {
                    Surface(shape = MaterialTheme.shapes.large, color = Color(0xFFFFEBEE), modifier = Modifier.fillMaxWidth()) {
                        Text(it, modifier = Modifier.padding(12.dp), color = Color(0xFFC62828))
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = {
                            focusManager.clearFocus()
                            val age = calculateAge(dateOfBirth)
                            val validation = validateRegister(
                                name = name,
                                username = username,
                                dateOfBirth = dateOfBirth,
                                age = age,
                                phone = phone,
                                email = email,
                                password = password
                            )
                            if (validation != null || age == null) {
                                errorMessage = validation ?: "Ngày sinh không hợp lệ."
                                return@Button
                            }

                            isLoading = true
                            viewModel.register(
                                email = email.trim(),
                                pass = password,
                                name = name.trim(),
                                age = age,
                                gender = gender,
                                username = username.trim(),
                                dateOfBirth = dateOfBirth.trim(),
                                phoneNumber = phone.trim()
                            ) { success, error ->
                                isLoading = false
                                if (success) {
                                    Toast.makeText(context, "Đăng ký thành công. Vui lòng đăng nhập.", Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                } else {
                                    errorMessage = error ?: "Đăng ký thất bại. Vui lòng thử lại."
                                }
                            }
                        }
                    ) {
                        Text("Đăng ký", fontWeight = FontWeight.ExtraBold)
                    }
                }

                TextButton(onClick = onNavigateBack) {
                    Text("Đã có tài khoản? Đăng nhập", color = Color(0xFF1976D2))
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction,
    onNext: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() })
    )
}

@Composable
private fun GenderSelector(value: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Giới tính", fontWeight = FontWeight.Bold, color = Color(0xFF0B3C7D))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = value == "male", onClick = { onChange("male") })
            Text("Nam")
            RadioButton(selected = value == "female", onClick = { onChange("female") })
            Text("Nữ")
            RadioButton(selected = value == "other", onClick = { onChange("other") })
            Text("Khác")
        }
    }
}

private fun validateRegister(
    name: String,
    username: String,
    dateOfBirth: String,
    age: Int?,
    phone: String,
    email: String,
    password: String
): String? {
    if (name.isBlank() || username.isBlank() || dateOfBirth.isBlank() || phone.isBlank() || email.isBlank() || password.isBlank()) {
        return "Vui lòng điền đầy đủ thông tin."
    }
    if (age == null) return "Ngày sinh không hợp lệ. Dùng định dạng yyyy-mm-dd."
    if (age <= 2) return "Tuổi của trẻ phải lớn hơn 2."
    if (!phone.matches(Regex("^\\d{10}$"))) return "Số điện thoại phải là 10 chữ số."
    if (!email.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) return "Email không hợp lệ."
    if (password.length <= 8 || !password.matches(Regex(".*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`].*"))) {
        return "Mật khẩu phải lớn hơn 8 ký tự và có ít nhất 1 ký tự đặc biệt."
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
