package de.tobiga.yougattme.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import de.tobiga.yougattme.ApplicationState
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.databinding.FragmentStairClimberBinding // <-- This will be generated
import de.tobiga.yougattme.gatt.fitnessmachine.StairClimberData

class StairClimberFragment : Fragment() {

    private var _binding: FragmentStairClimberBinding? = null
    private val binding get() = _binding!!

    private lateinit var appState: ApplicationState
    private lateinit var mainViewModel: ApplicationViewModel
    private lateinit var stairClimberData: StairClimberData

    companion object {
        fun newInstance() = StairClimberFragment()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        appState = ApplicationState.getInstance(context)
        appState.activateFitnessMachineProfile("stair_climber")
        mainViewModel = ViewModelProvider(requireActivity()).get(ApplicationViewModel::class.java)
        // Make sure you add this to your ViewModel
        stairClimberData = mainViewModel.ftmsStairClimberData
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Use the generated binding class
        _binding = FragmentStairClimberBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.stairClimberData = stairClimberData
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}