package de.tobiga.yougattme.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.tobiga.yougattme.ApplicationState
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.databinding.FragmentTreadmillBinding
import de.tobiga.yougattme.gatt.fitnessmachine.TreadmillData

class TreadmillFragment : Fragment() {

    private var _binding: FragmentTreadmillBinding? = null
    private val binding get() = _binding!!

    private lateinit var appState: ApplicationState

    companion object {
        fun newInstance() = TreadmillFragment()
    }

    private lateinit var mainViewModel: ApplicationViewModel
    private lateinit var treadmillData: TreadmillData

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appState = ApplicationState.getInstance(context)
        // Set the mode to treadmill.
        appState.setFitnessMachineMode("treadmill")
        mainViewModel = ViewModelProvider(requireActivity()).get(ApplicationViewModel::class.java)
        treadmillData = mainViewModel.ftmsTreadmillData
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTreadmillBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.treadmillData = treadmillData
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Prefill input fields with current values.
        binding.etInstantaneousSpeedInput.setText(treadmillData.instantaneousSpeed.toString())
        binding.etStepRateInput.setText(treadmillData.instantaneousStepRate.toString())
        binding.etHeartRateInput.setText(treadmillData.heartRate.toString())

        // Set initial checkbox state for heart rate.
        binding.cbHeartRate.isChecked = treadmillData.includeHeartRate

        // Setup text watchers for each EditText.
        fun setupTextWatcher(editText: android.widget.EditText, onChange: (String) -> Unit) {
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { onChange(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        setupTextWatcher(binding.etInstantaneousSpeedInput) {
            val value = it.toFloatOrNull() ?: 0f
            treadmillData.instantaneousSpeed = value
        }
        setupTextWatcher(binding.etStepRateInput) {
            val value = it.toIntOrNull() ?: 0
            treadmillData.instantaneousStepRate = value
        }
        setupTextWatcher(binding.etHeartRateInput) {
            val value = it.toIntOrNull() ?: 0
            treadmillData.heartRate = value
        }

        // Setup checkbox listener for Heart Rate.
        binding.cbHeartRate.setOnCheckedChangeListener { _, isChecked ->
            treadmillData.includeHeartRate = isChecked
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
