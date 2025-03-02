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
import de.tobiga.yougattme.databinding.FragmentRunningSpeedAndCadenceBinding
import de.tobiga.yougattme.gatt.rsc.RunningSpeedAndCadenceProfileData

class RunningSpeedAndCadenceFragment : Fragment() {

    private var _binding: FragmentRunningSpeedAndCadenceBinding? = null
    private val binding get() = _binding!!

    private lateinit var mainViewModel: ApplicationViewModel
    private lateinit var runningSpeedAndCadenceProfileData: RunningSpeedAndCadenceProfileData


    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainViewModel = ViewModelProvider(requireActivity()).get(ApplicationViewModel::class.java)
        runningSpeedAndCadenceProfileData = mainViewModel.runningSpeedAndCadenceData
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRunningSpeedAndCadenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSubmit.setOnClickListener {
            val speedText = binding.etSpeed.text.toString()
            val cadenceText = binding.etCadence.text.toString()

            try {
                val speed = speedText.toDouble()         // Running speed in km/h
                val cadence = cadenceText.toInt()         // Running cadence in rpm

                runningSpeedAndCadenceProfileData.postInstantaneousSpeed(speed / 3.6)
                runningSpeedAndCadenceProfileData.postInstantaneousCadence(cadence)

                Toast.makeText(requireContext(), "Running data submitted", Toast.LENGTH_SHORT).show()
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
