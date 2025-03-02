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
import de.tobiga.yougattme.databinding.FragmentCyclingSpeedAndCadenceBinding
import de.tobiga.yougattme.gatt.csc.CyclingSpeedAndCadenceProfileData

class CyclingSpeedAndCadenceFragment : Fragment() {

    private var _binding: FragmentCyclingSpeedAndCadenceBinding? = null
    private val binding get() = _binding!!

    private lateinit var mainViewModel: ApplicationViewModel
    private lateinit var cyclingSpeedAndCadenceProfileData: CyclingSpeedAndCadenceProfileData


    private lateinit var otherMainViewModel: ApplicationViewModel

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainViewModel = ViewModelProvider(requireActivity()).get(ApplicationViewModel::class.java)
        cyclingSpeedAndCadenceProfileData = mainViewModel.cyclingSpeedAndCadenceData
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCyclingSpeedAndCadenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up Submit button listener
        binding.btnSubmit.setOnClickListener {
            val speedText = binding.etSpeed.text.toString()
            val cadenceText = binding.etCadence.text.toString()
            val circumferenceText = binding.etWheelCircumference.text.toString()

            try {
                val speed = speedText.toFloat()         // km/h
                val cadence = cadenceText.toInt()         // rpm
                val circumference = circumferenceText.toFloat() // in centimeters (or convert as needed)

                cyclingSpeedAndCadenceProfileData.postInstantaneousSpeed(speed);
                cyclingSpeedAndCadenceProfileData.postInstantaneousCadence(cadence);
                cyclingSpeedAndCadenceProfileData.postWheelCircumference(circumference);

                Toast.makeText(requireContext(), "Data submitted successfully", Toast.LENGTH_SHORT).show()
            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), "Please enter valid numbers", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
