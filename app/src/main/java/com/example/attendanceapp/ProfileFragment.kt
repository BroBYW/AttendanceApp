package com.example.attendanceapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val username = activity?.intent?.getStringExtra("username") ?: "User"

        // Set profile data
        val tvProfileInitial = view.findViewById<TextView>(R.id.tvProfileInitial)
        val tvProfileName = view.findViewById<TextView>(R.id.tvProfileName)
        val tvProfileUsername = view.findViewById<TextView>(R.id.tvProfileUsername)

        tvProfileInitial.text = username.first().uppercase()
        tvProfileName.text = username
        tvProfileUsername.text = username

        // App version
        val tvVersion = view.findViewById<TextView>(R.id.tvProfileVersion)
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            tvVersion.text = "v${pInfo.versionName} (Build ${pInfo.longVersionCode})"
        } catch (_: Exception) {
            tvVersion.text = "v1.0"
        }

        // Logout button
        val btnLogout = view.findViewById<MaterialButton>(R.id.btnProfileLogout)
        btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Logout") { _, _ ->
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    activity?.finish()
                }
                .show()
        }
    }
}
