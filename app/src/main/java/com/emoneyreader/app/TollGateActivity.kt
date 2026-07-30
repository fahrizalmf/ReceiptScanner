package com.emoneyreader.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.emoneyreader.app.adapter.TollGateAdapter
import com.emoneyreader.app.data.AppDatabase
import com.emoneyreader.app.data.TollGate
import com.emoneyreader.app.databinding.ActivityTollGateBinding
import kotlinx.coroutines.launch

class TollGateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTollGateBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: TollGateAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTollGateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Kelola Gerbang Tol"

        db = AppDatabase.getInstance(this)

        adapter = TollGateAdapter(emptyList()) { tollGate ->
            lifecycleScope.launch { db.tollGateDao().delete(tollGate) }
        }
        binding.recyclerTollGate.layoutManager = LinearLayoutManager(this)
        binding.recyclerTollGate.adapter = adapter

        binding.btnAdd.setOnClickListener {
            val name = binding.etTollGateName.text.toString().trim()
            if (name.isNotEmpty()) {
                lifecycleScope.launch {
                    db.tollGateDao().insert(TollGate(name = name))
                    binding.etTollGateName.text.clear()
                }
            }
        }

        lifecycleScope.launch {
            db.tollGateDao().getAll().collect { list ->
                adapter.updateData(list)
            }
        }
    }
}
