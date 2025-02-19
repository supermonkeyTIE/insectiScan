package com.insectiscan

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Region spinner code
        val regionSpinner = findViewById<AutoCompleteTextView>(R.id.regionSpinner)

        // Create an array of regions
        val regions = arrayOf("North", "South", "East", "West", "Central")

        // Create an ArrayAdapter
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            regions
        )

        // Apply the adapter to the spinner
        regionSpinner.setAdapter(adapter)

        regionSpinner.setOnItemClickListener { parent, view, pos, id ->
            val selectedRegion = parent.getItemAtPosition(pos).toString()
            Toast.makeText(this, "Selected Region: $selectedRegion", Toast.LENGTH_SHORT).show()
        }

        // Camera button code
        findViewById<MaterialButton>(R.id.cameraScanButton).setOnClickListener {
            startActivityForResult(Intent(this, CameraActivity::class.java), REQUEST_IMAGE_CAPTURE)
        }

        // Severity slider code
        val severitySlider = findViewById<Slider>(R.id.severitySlider)
        val severityLabel = findViewById<TextView>(R.id.severityLabel)

        severitySlider.addOnChangeListener { slider, value, fromUser ->
            severityLabel.text = "Selected: ${value.toInt()}"
        }

        // Analyze button code
        findViewById<MaterialButton>(R.id.analyzeButton).setOnClickListener {
            // TODO: Implement analysis logic
            Toast.makeText(this, "Analysis functionality coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Handle the captured image here
        }
    }
}