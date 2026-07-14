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
import de.tobiga.yougattme.databinding.FragmentStepClimberBinding
import de.tobiga.yougattme.gatt.fitnessmachine.StepClimberData

class StepClimberFragment : Fragment() {

    private var _binding: FragmentStepClimberBinding? = null
    private val binding get() = _binding!!

    private lateinit var appState: ApplicationState
    private lateinit var mainViewModel: ApplicationViewModel
    private lateinit var stepClimberData: StepClimberData

    companion object {
        fun newInstance() = StepClimberFragment()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appState = ApplicationState.getInstance(context)
        appState.activateFitnessMachineProfile("step_climber")
        mainViewModel = ViewModelProvider(requireActivity()).get(ApplicationViewModel::class.java)
        // You will need to add this property to your ViewModel
        stepClimberData = mainViewModel.ftmsStepClimberData
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStepClimberBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.stepClimberData = stepClimberData
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup listeners for EditTexts
        binding.etStepsPerMinuteInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                stepClimberData.stepsPerMinute = s.toString().toIntOrNull() ?: 0
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.etEnergyPerMinuteInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                stepClimberData.energyPerMinute = s.toString().toIntOrNull() ?: 0
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Setup listeners for CheckBoxes
        binding.cbIncludeStepsPerMinute.setOnCheckedChangeListener { _, isChecked ->
            stepClimberData.includeStepsPerMinute = isChecked
        }
        binding.cbIncludePositiveElevationGain.setOnCheckedChangeListener { _, isChecked ->
            stepClimberData.includePositiveElevationGain = isChecked
        }
        binding.cbIncludeExpendedEnergy.setOnCheckedChangeListener { _, isChecked ->
            stepClimberData.includeExpendedEnergy = isChecked
        }
        binding.cbIncludeHeartRate.setOnCheckedChangeListener { _, isChecked ->
            stepClimberData.includeHeartRate = isChecked
        }
        binding.cbIncludeElapsedTime.setOnCheckedChangeListener { _, isChecked ->
            stepClimberData.includeElapsedTime = isChecked
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}