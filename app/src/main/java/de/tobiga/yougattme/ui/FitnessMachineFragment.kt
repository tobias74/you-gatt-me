package de.tobiga.yougattme.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import de.tobiga.yougattme.R
import de.tobiga.yougattme.databinding.FragmentFitnessMachineBinding

class FitnessMachineFragment : Fragment() {

    companion object {
        fun newInstance() = FitnessMachineFragment()
    }

    private var _binding: FragmentFitnessMachineBinding? = null
    private val binding get() = _binding!!

    private val modeValues by lazy { resources.getStringArray(R.array.fitness_machine_entry_values) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFitnessMachineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Set up the spinner adapter
        binding.fitnessMachineModeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            modeValues
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Set listener on the spinner
        binding.fitnessMachineModeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long,
                ) {
                    val selectedMode = modeValues[position]

                    // Create the proper child fragment based on the selection.
                    val childFragment = when (selectedMode) {
                        "Rower" -> {
                            RowerFragment.newInstance()
                        }
                        "Indoor Bike" -> {
                            IndoorBikeFragment.newInstance()
                        }
                        "Treadmill" -> {
                            TreadmillFragment.newInstance()
                        }
                        "Cross Trainer" -> {
                            CrossTrainerFragment.newInstance()
                        }
                        else -> {
                            RowerFragment.newInstance()
                        }
                    }

                    // Replace the child container with the selected fragment immediately.
                    childFragmentManager.commitNow {
                        replace(R.id.childContainer, childFragment)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // Optional: handle the case when nothing is selected.
                }
            }

        // Optionally force the spinner to call onItemSelected on initial display.
        binding.fitnessMachineModeSpinner.setSelection(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}