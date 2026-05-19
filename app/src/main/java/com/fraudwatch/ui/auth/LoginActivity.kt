package com.fraudwatch.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.fraudwatch.MainActivity
import com.fraudwatch.databinding.ActivityLoginBinding
import com.fraudwatch.utils.gone
import com.fraudwatch.utils.visible
import com.fraudwatch.viewmodel.AuthViewModel
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (authViewModel.currentUser != null) {
            goToMain()
            return
        }

        observeViewModel()
        setupClicks()
    }

    private fun observeViewModel() {
        authViewModel.authState.observe(this) { state ->
            if (state is AuthViewModel.AuthState.Success) goToMain()
        }

        authViewModel.loading.observe(this) { loading ->
            if (loading) binding.progressBar.visible() else binding.progressBar.gone()
            binding.btnLogin.isEnabled = !loading
            binding.btnGoRegister.isEnabled = !loading
        }

        authViewModel.errorMessage.observe(this) { msg ->
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupClicks() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            authViewModel.login(email, password)
        }

        binding.btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.btnTestMode.setOnClickListener {
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
