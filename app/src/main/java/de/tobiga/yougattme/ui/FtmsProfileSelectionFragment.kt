package de.tobiga.yougattme.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import de.tobiga.yougattme.R
import de.tobiga.yougattme.databinding.FragmentFtmsProfileSelectionBinding

class FtmsProfileSelectionFragment : Fragment() {

    private var _binding: FragmentFtmsProfileSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFtmsProfileSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Set up the spinner adapter using a string-array resource.
        binding.profileSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.ftms_profile_entries)
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val selectedProfile = parent?.getItemAtPosition(position) as String
                val fragment = when (selectedProfile) {
                    "Rower" -> RowerFragment.newInstance()
                    "Indoor Bike" -> IndoorBikeFragment.newInstance()
                    "Treadmill" -> TreadmillFragment.newInstance()
                    "Cross Trainer" -> CrossTrainerFragment.newInstance()
                    else -> RowerFragment.newInstance()
                }
                // Replace the child container with the chosen fragment.
                childFragmentManager.commit {
                    replace(R.id.childContainer, fragment)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
