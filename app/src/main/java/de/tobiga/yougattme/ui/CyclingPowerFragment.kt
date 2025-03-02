package de.tobiga.yougattme.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.databinding.FragmentCyclingPowerBinding
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerProfileData

class CyclingPowerFragment : Fragment() {

    private var _binding: FragmentCyclingPowerBinding? = null
    private val binding get() = _binding!!


    private lateinit var mainViewModel: ApplicationViewModel
    private lateinit var cyclingPowerProfileData: CyclingPowerProfileData

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainViewModel = ViewModelProvider(requireActivity()).get(ApplicationViewModel::class.java)
        cyclingPowerProfileData = mainViewModel.cyclingPowerData
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCyclingPowerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSubmit.setOnClickListener {
            val powerText = binding.etPower.text.toString()
            if (powerText.isNotBlank()) {
                try {
                    val power = powerText.toInt() // User input in Watts
                    // Call your ViewModel method to pass along the target power value.
                    cyclingPowerProfileData.postInstantaneousPower(power)
                    Toast.makeText(requireContext(), "Cycling Power set to $power W", Toast.LENGTH_SHORT).show()
                } catch (e: NumberFormatException) {
                    Toast.makeText(requireContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a power value", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
