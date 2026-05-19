package com.fraudwatch.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.fraudwatch.MainActivity
import com.fraudwatch.databinding.ActivityRegisterBinding
import com.fraudwatch.utils.gone
import com.fraudwatch.utils.visible
import com.fraudwatch.viewmodel.AuthViewModel
import com.google.android.material.snackbar.Snackbar

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeViewModel()
        setupClicks()
    }

    private fun observeViewModel() {
        authViewModel.authState.observe(this) { state ->
            if (state is AuthViewModel.AuthState.Success) {
                startActivity(Intent(this, MainActivity::class.java))
                finishAffinity()
            }
        }

        authViewModel.loading.observe(this) { loading ->
            if (loading) binding.progressBar.visible() else binding.progressBar.gone()
            binding.btnRegister.isEnabled = !loading
        }

        authViewModel.errorMessage.observe(this) { msg ->
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupClicks() {
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirm = binding.etConfirmPassword.text.toString()
            authViewModel.register(email, password, confirm)
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}
