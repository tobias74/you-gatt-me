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
import de.tobiga.yougattme.databinding.FragmentCrossTrainerBinding
import de.tobiga.yougattme.gatt.fitnessmachine.CrossTrainerData

class CrossTrainerFragment : Fragment() {

    private var _binding: FragmentCrossTrainerBinding? = null
    private val binding get() = _binding!!

    private lateinit var appState: ApplicationState

    companion object {
        fun newInstance() = CrossTrainerFragment()
    }

    private lateinit var mainViewModel: ApplicationViewModel
    private lateinit var crossTrainerData: CrossTrainerData

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appState = ApplicationState.getInstance(context)
        appState.setFitnessMachineMode("cross_trainer")
        mainViewModel = ViewModelProvider(requireActivity()).get(ApplicationViewModel::class.java)
        crossTrainerData = mainViewModel.ftmsCrossTrainerData
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCrossTrainerBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.crossTrainerData = crossTrainerData
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Prefill input fields with current values.
        binding.etInstantaneousSpeedInput.setText(crossTrainerData.instantaneousSpeed.toString())
        binding.etInstantaneousPowerInput.setText(crossTrainerData.instantaneousPower.toString())
        binding.etHeartRateInput.setText(crossTrainerData.heartRate.toString())
        binding.etInstantaneousStepRateInput.setText(crossTrainerData.instantaneousStepsPerMinute.toString())

        // Setup text watchers for user input fields.
        fun setupTextWatcher(editText: android.widget.EditText, onChange: (String) -> Unit) {
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { onChange(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        setupTextWatcher(binding.etInstantaneousSpeedInput) {
            val value = it.toFloatOrNull() ?: 0f
            crossTrainerData.instantaneousSpeed = value
        }

        setupTextWatcher(binding.etInstantaneousPowerInput) {
            val value = it.toIntOrNull() ?: 0
            crossTrainerData.instantaneousPower = value
        }

        setupTextWatcher(binding.etHeartRateInput) {
            val value = it.toIntOrNull() ?: 0
            crossTrainerData.heartRate = value
        }

        setupTextWatcher(binding.etInstantaneousStepRateInput) {
            val value = it.toIntOrNull() ?: 0
            crossTrainerData.instantaneousStepsPerMinute = value
        }

        // Checkbox listeners for enabling/disabling optional fields.
        binding.cbInstantaneousPower.setOnCheckedChangeListener { _, isChecked ->
            crossTrainerData.includeInstantaneousPower = isChecked
        }
        binding.cbHeartRate.setOnCheckedChangeListener { _, isChecked ->
            crossTrainerData.includeHeartRate = isChecked
        }
        binding.cbTotalDistance.setOnCheckedChangeListener { _, isChecked ->
            crossTrainerData.includeTotalDistance = isChecked
        }
        binding.cbElapsedTime.setOnCheckedChangeListener { _, isChecked ->
            crossTrainerData.includeElapsedTime = isChecked
        }

        // Button click listeners.
        binding.btnReset.setOnClickListener {
            // Reset input fields after a reset.
            binding.etInstantaneousSpeedInput.setText(crossTrainerData.instantaneousSpeed.toString())
            binding.etInstantaneousPowerInput.setText(crossTrainerData.instantaneousPower.toString())
            binding.etHeartRateInput.setText(crossTrainerData.heartRate.toString())
            binding.etInstantaneousStepRateInput.setText(crossTrainerData.instantaneousStepsPerMinute.toString())
        }

        binding.btnApply.setOnClickListener {
            // Read values from input fields and apply to the model.
            val speed = binding.etInstantaneousSpeedInput.text.toString().toFloatOrNull() ?: 0f
            val power = binding.etInstantaneousPowerInput.text.toString().toIntOrNull() ?: 0
            val heartRate = binding.etHeartRateInput.text.toString().toIntOrNull() ?: 0
            val stepRate = binding.etInstantaneousStepRateInput.text.toString().toIntOrNull() ?: 0

            crossTrainerData.instantaneousSpeed = speed
            crossTrainerData.instantaneousPower = power
            crossTrainerData.heartRate = heartRate
            crossTrainerData.instantaneousStepsPerMinute = stepRate
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
