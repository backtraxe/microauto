package com.microauto.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.microauto.databinding.ActivityTutorialBinding
import com.microauto.script.ScriptRepository

class TutorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTutorialBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdge.apply(this, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnRestoreBuiltins.setOnClickListener {
            val added = ScriptRepository(this).ensureBuiltins()
            Toast.makeText(
                this,
                if (added > 0) "已添加 $added 个内置示例" else "内置示例已存在",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
