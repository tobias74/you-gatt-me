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
import de.tobiga.yougattme.databinding.FragmentRowerBinding
import de.tobiga.yougattme.gatt.fitnessmachine.RowerData

class RowerFragment : Fragment() {

    private var _binding: FragmentRowerBinding? = null
    private val binding get() = _binding!!

    private lateinit var appState: ApplicationState

    companion object {
        fun newInstance() = RowerFragment()
    }

    private lateinit var mainViewModel: ApplicationViewModel
    private lateinit var rowerData: RowerData

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appState = ApplicationState.getInstance(context)
        appState.setFitnessMachineMode("rower")
        mainViewModel = ViewModelProvider(requireActivity()).get(ApplicationViewModel::class.java)
        rowerData = mainViewModel.ftmsRowerData
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRowerBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.rowerData = rowerData
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Prefill input fields with current values
        binding.etStrokeRateInput.setText(rowerData.strokeRate.toString())
        binding.etInstPaceInput.setText(rowerData.instantaneousPace.toString())
        binding.etInstPowerInput.setText(rowerData.instantaneousPower.toString())
        binding.etHeartRateInput.setText(rowerData.heartRate.toString())

        // Set checkbox states
        binding.cbInstPace.isChecked = rowerData.includeInstantaneousPace
        binding.cbInstPower.isChecked = rowerData.includeInstantaneousPower
        binding.cbHeartRate.isChecked = rowerData.includeHeartRate

        // Setup text watchers for real-time updates
        fun setupTextWatcher(editText: android.widget.EditText, onChange: (String) -> Unit) {
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { onChange(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        setupTextWatcher(binding.etStrokeRateInput) {
            val value = it.toIntOrNull() ?: 0
            rowerData.strokeRate = value
        }
        setupTextWatcher(binding.etInstPaceInput) {
            val value = it.toIntOrNull() ?: 0
            rowerData.instantaneousPace = value
        }
        setupTextWatcher(binding.etInstPowerInput) {
            val value = it.toIntOrNull() ?: 0
            rowerData.instantaneousPower = value
        }
        setupTextWatcher(binding.etHeartRateInput) {
            val value = it.toIntOrNull() ?: 0
            rowerData.heartRate = value
        }

        // Setup checkbox listeners
        binding.cbInstPace.setOnCheckedChangeListener { _, isChecked ->
            rowerData.includeInstantaneousPace = isChecked
        }
        binding.cbInstPower.setOnCheckedChangeListener { _, isChecked ->
            rowerData.includeInstantaneousPower = isChecked
        }
        binding.cbHeartRate.setOnCheckedChangeListener { _, isChecked ->
            rowerData.includeHeartRate = isChecked
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
