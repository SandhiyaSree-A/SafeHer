package com.safeher.app.ui.auth

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeher.app.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: (User) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isSignUpMode by viewModel.isSignUpMode.collectAsState()
    val name by viewModel.name.collectAsState()
    val phoneNumber by viewModel.phoneNumber.collectAsState()
    val password by viewModel.password.collectAsState()
    val confirmPassword by viewModel.confirmPassword.collectAsState()
    val otpCode by viewModel.otpCode.collectAsState()
    val disambiguationPhone by viewModel.disambiguationPhone.collectAsState()
    val otpError by viewModel.otpError.collectAsState()
    val isVerifyingOtp by viewModel.isVerifyingOtp.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onAuthSuccess((uiState as AuthUiState.Success).user)
        }
    }

    if (uiState is AuthUiState.Loading) {
        ScootyLoadingScreen(
            message = if (isSignUpMode) "Creating account & signing in..." else "Loading SafeHer..."
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "SafeHer",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "SafeHer Security Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 12.dp)
                )

                Text(
                    text = if (isSignUpMode) "Create SafeHer Account" else "Welcome Back",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (isSignUpMode) "Verify phone once via OTP, then set your password" else "Sign in with your Name and Password",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // Tab Switcher: Sign In vs Sign Up
                TabRow(
                    selectedTabIndex = if (isSignUpMode) 1 else 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Tab(
                        selected = !isSignUpMode,
                        onClick = { viewModel.toggleAuthMode(false) },
                        text = { Text("Sign In", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = isSignUpMode,
                        onClick = { viewModel.toggleAuthMode(true) },
                        text = { Text("Sign Up", fontWeight = FontWeight.SemiBold) }
                    )
                }

                // Error Message Banner
                AnimatedVisibility(visible = uiState is AuthUiState.Error) {
                    if (uiState is AuthUiState.Error) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = (uiState as AuthUiState.Error).message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (!isSignUpMode) {
                    // ==============================
                    // SIGN IN MODE (Name + Password)
                    // ==============================
                    OutlinedTextField(
                        value = name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                        trailingIcon = {
                            val image = if (passwordVisible)
                                Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff

                            val description = if (passwordVisible) "Hide password" else "Show password"

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    // Disambiguation prompt if multiple accounts share this name
                    if (uiState is AuthUiState.DisambiguationRequired) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = (uiState as AuthUiState.DisambiguationRequired).message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = disambiguationPhone,
                                    onValueChange = { viewModel.updateDisambiguationPhone(it) },
                                    label = { Text("Your Phone Number (to disambiguate)") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.loginWithPassword() },
                        enabled = uiState !is AuthUiState.Loading && name.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = { viewModel.toggleAuthMode(true) },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Don't have an account? Sign Up")
                    }

                } else {
                    // ============================================
                    // SIGN UP MODE (Name + Phone -> OTP -> Password)
                    // ============================================
                    when (uiState) {
                        is AuthUiState.OtpVerified -> {
                            // Step 3: Set Password
                            Text(
                                text = "Set your login password",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { viewModel.updatePassword(it) },
                                label = { Text("Create Password (min. 6 chars)") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                                trailingIcon = {
                                    val image = if (passwordVisible)
                                        Icons.Filled.Visibility
                                    else Icons.Filled.VisibilityOff

                                    val description = if (passwordVisible) "Hide password" else "Show password"

                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = image, contentDescription = description)
                                    }
                                },
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { viewModel.updateConfirmPassword(it) },
                                label = { Text("Confirm Password") },
                                leadingIcon = { Icon(Icons.Default.Check, contentDescription = "Confirm Password") },
                                trailingIcon = {
                                    val image = if (confirmPasswordVisible)
                                        Icons.Filled.Visibility
                                    else Icons.Filled.VisibilityOff

                                    val description = if (confirmPasswordVisible) "Hide password" else "Show password"

                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(imageVector = image, contentDescription = description)
                                    }
                                },
                                visualTransformation = if (confirmPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            )

                            Button(
                                onClick = { viewModel.completeSignupWithPassword() },
                                enabled = uiState !is AuthUiState.Loading && password.length >= 6 && password == confirmPassword,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("Create Account & Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        is AuthUiState.OtpSent -> {
                            // Step 2: Verify OTP
                            Text(
                                text = "OTP code sent to $phoneNumber",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { viewModel.updateOtpCode(it) },
                                label = { Text("Enter 6-digit OTP") },
                                leadingIcon = { Icon(Icons.Default.Pin, contentDescription = "OTP Code") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            )
otpError?.let {
    Text(
        text = it,
        color = MaterialTheme.colorScheme.error,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}


                            Button(
                                onClick = { viewModel.verifySignupOtp() },
                                enabled = uiState !is AuthUiState.Loading && otpCode.length >= 6,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("Verify OTP & Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }

                            TextButton(
                                onClick = { viewModel.toggleAuthMode(true) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Change Phone Number")
                            }
                        }

                        else -> {
                            // Step 1: Name + Phone
                            OutlinedTextField(
                                value = name,
                                onValueChange = { viewModel.updateName(it) },
                                label = { Text("Full Name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { viewModel.updatePhoneNumber(it) },
                                label = { Text("Phone Number (e.g. +14155552671)") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone Number") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            )

                            Button(
                                onClick = {
                                    (context as? Activity)?.let { activity ->
                                        viewModel.sendSignupOtp(activity)
                                    }
                                },
                                enabled = uiState !is AuthUiState.Loading && name.isNotBlank() && phoneNumber.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("Send Verification OTP", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }

                            TextButton(
                                onClick = { viewModel.toggleAuthMode(false) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Already have an account? Sign In")
                            }
                        }
                    }
                }
            }
        }
    }
}

