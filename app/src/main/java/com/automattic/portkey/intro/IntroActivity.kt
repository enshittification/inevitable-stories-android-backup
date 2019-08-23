package com.automattic.portkey.intro

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.automattic.photoeditor.util.PermissionUtils
import com.automattic.portkey.AppPrefs
import com.automattic.portkey.MainActivity
import com.automattic.portkey.R

class IntroActivity : AppCompatActivity(), IntroFragment.OnFragmentInteractionListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        setSupportActionBar(toolbar)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        showFragment(IntroFragment(), IntroFragment.TAG)
    }

    override fun onSupportNavigateUp() =
        Navigation.findNavController(this, R.id.nav_host_fragment).navigateUp()

    override fun onGetStartedPressed() {
        AppPrefs.setIntroRequired(false)
        if (PermissionUtils.allRequiredPermissionsGranted(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            // TODO Show permissions request screen
        }
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container, fragment, tag)
        fragmentTransaction.commit()
    }
}
