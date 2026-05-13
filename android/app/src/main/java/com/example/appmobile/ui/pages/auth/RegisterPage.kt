package com.example.appmobile.ui.pages.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appmobile.R
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.remote.FirebaseAuthHelper
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.UserRepository
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

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background Image (Consistent with LoginPage)
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Logo and Brand
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "EmoGarden",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7CB9E8)
                        )
                    }

                    Text(
                        "Tạo tài khoản",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A4A4A)
                    )
                    Text(
                        "Điền thông tin của bé để đồng bộ hồ sơ học tập",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    // Input Fields
                    AuthTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        placeholder = "Họ và tên của bé",
                        imeAction = ImeAction.Next
                    )

                    AuthTextField(
                        value = username,
                        onValueChange = { username = it; errorMessage = null },
                        placeholder = "Tên đăng nhập",
                        imeAction = ImeAction.Next
                    )

                    GenderSelector(value = gender, onChange = { gender = it })

                    AuthTextField(
                        value = dateOfBirth,
                        onValueChange = { dateOfBirth = it.take(10); errorMessage = null },
                        placeholder = "Ngày sinh (yyyy-mm-dd)",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )

                    AuthTextField(
                        value = phone,
                        onValueChange = { if (it.all(Char::isDigit)) phone = it.take(10); errorMessage = null },
                        placeholder = "Số điện thoại phụ huynh",
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    )

                    AuthTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        placeholder = "Email phụ huynh",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )

                    AuthTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        placeholder = "Mật khẩu",
                        isPassword = true,
                        imeAction = ImeAction.Done
                    )

                    errorMessage?.let {
                        Text(it, color = Color.Red, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoading) {
                        CircularProgressIndicator(color = Color(0xFFFFA726))
                    } else {
                        Button(
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
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(4.dp, RoundedCornerShape(28.dp)),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726))
                        ) {
                            Text("Đăng ký", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Đã có tài khoản? ", color = Color.DarkGray, fontSize = 14.sp)
                        Text(
                            "Đăng nhập",
                            color = Color(0xFF8D6E63),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateBack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.Gray) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFFAED9FF),
            unfocusedBorderColor = Color(0xFFAED9FF),
            focusedContainerColor = Color(0xFFF0F8FF),
            unfocusedContainerColor = Color(0xFFF0F8FF)
        )
    )
}

@Composable
private fun GenderSelector(value: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Giới tính", fontWeight = FontWeight.Bold, color = Color(0xFF4A4A4A), fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = value == "male", onClick = { onChange("male") }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFA726)))
                Text("Nam", fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = value == "female", onClick = { onChange("female") }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFA726)))
                Text("Nữ", fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = value == "other", onClick = { onChange("other") }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFA726)))
                Text("Khác", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CloudIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50)
    ) {}
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
    if (password.length < 8 || !password.matches(Regex(".*[!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`].*"))) {
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