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
import de.tobiga.yougattme.databinding.FragmentIndoorBikeBinding
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorBikeData

class IndoorBikeFragment : Fragment() {

    private var _binding: FragmentIndoorBikeBinding? = null
    private val binding get() = _binding!!

    private lateinit var appState: ApplicationState

    companion object {
        fun newInstance() = IndoorBikeFragment()
    }

    private lateinit var mainViewModel: ApplicationViewModel
    private lateinit var indoorBikeData: IndoorBikeData

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appState = ApplicationState.getInstance(context)
        appState.setFitnessMachineMode("indoor_bike")
        mainViewModel = ViewModelProvider(requireActivity()).get(ApplicationViewModel::class.java)
        indoorBikeData = mainViewModel.ftmsIndoorBikeData
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIndoorBikeBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.indoorBikeData = indoorBikeData
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Prefill input fields with current values
        binding.etInstSpeedInput.setText(indoorBikeData.instantaneousSpeed.toString())
        binding.etInstCadenceInput.setText(indoorBikeData.instantaneousCadence.toString())
        binding.etInstPowerInput.setText(indoorBikeData.instantaneousPower.toString())
        binding.etHeartRateInput.setText(indoorBikeData.heartRate.toString())

        // Set checkbox states
        binding.cbHeartRate.isChecked = indoorBikeData.includeHeartRate

        // Setup text watchers for real-time updates
        fun setupTextWatcher(editText: android.widget.EditText, onChange: (String) -> Unit) {
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { onChange(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        setupTextWatcher(binding.etInstSpeedInput) {
            val value = it.toFloatOrNull() ?: 0f
            indoorBikeData.instantaneousSpeed = value
        }
        setupTextWatcher(binding.etInstCadenceInput) {
            val value = it.toIntOrNull() ?: 0
            indoorBikeData.instantaneousCadence = value
        }
        setupTextWatcher(binding.etInstPowerInput) {
            val value = it.toIntOrNull() ?: 0
            indoorBikeData.instantaneousPower = value
        }
        setupTextWatcher(binding.etHeartRateInput) {
            val value = it.toIntOrNull() ?: 0
            indoorBikeData.heartRate = value
        }

        // Setup checkbox listener for heart rate
        binding.cbHeartRate.setOnCheckedChangeListener { _, isChecked ->
            indoorBikeData.includeHeartRate = isChecked
        }

        // Observe received control point values
        mainViewModel.controlHasBeenTakenInControlPoint.observe(viewLifecycleOwner) { taken ->
            binding.tvReceivedControlTaken.text = "Control Taken: ${if (taken) "Yes" else "No"}"
        }
        mainViewModel.receivedTargetPowerInControlPoint.observe(viewLifecycleOwner) { power ->
            binding.tvReceivedTargetPower.text = "Target Power: ${power} W"
        }
        mainViewModel.ftmsReceivedTargetInclination.observe(viewLifecycleOwner) { inclination ->
            binding.tvReceivedTargetInclination.text = "Target Inclination: ${inclination}°"
        }
        mainViewModel.receivedIndoorBikeGrade.observe(viewLifecycleOwner) { grade ->
            binding.tvReceivedIndoorBikeGrade.text = "Indoor Bike Grade: ${grade}"
        }
        mainViewModel.fitnessMachineHasBeenStarted.observe(viewLifecycleOwner) { started ->
            binding.tvReceivedFitnessMachineStarted.text = "Fitness Machine Started: ${if (started) "Yes" else "No"}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
