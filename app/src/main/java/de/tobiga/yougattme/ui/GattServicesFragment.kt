package de.tobiga.yougattme.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import de.tobiga.yougattme.ApplicationState
import de.tobiga.yougattme.R
import de.tobiga.yougattme.databinding.FragmentGattServicesBinding

// Data model for a GATT profile definition.
data class GattProfileDef(
    val key: String,
    val fragmentFactory: () -> Fragment
)

class GattServicesFragment : Fragment() {

    private var _binding: FragmentGattServicesBinding? = null
    private val binding get() = _binding!!
    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var switchAB: SwitchCompat
    private lateinit var applicationState: ApplicationState

    companion object {
        const val GATT_SERVER_ACTIVATED = "gatt_server_activated"
        const val LAST_SELECTED_PROFILE = "last_selected_profile"
    }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Handle preference changes if needed
    }

    // Define the list of profiles with keys and corresponding fragment factories.
    private val gattProfiles = listOf(
        GattProfileDef("fitness_machine") { FitnessMachineFragment() },
        GattProfileDef("cycling_speed_cadence") { CyclingSpeedAndCadenceFragment() },
        GattProfileDef("cycling_power") { CyclingPowerFragment() },
        GattProfileDef("running_speed_cadence") { RunningSpeedAndCadenceFragment() },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)  // Enable options menu
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGattServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Utility to fetch a string resource by name.
    private fun getStringResourceByName(name: String): String {
        val resId = resources.getIdentifier(name, "string", requireContext().packageName)
        return if (resId != 0) getString(resId) else name
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applicationState = ApplicationState.getInstance(requireContext())

        val spinner: Spinner = binding.profileSpinner

        // Build a list of display labels using our keys.
        val profileDisplayList = gattProfiles.map {
            getStringResourceByName("spinner_gatt_profile_${it.key}")
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            profileDisplayList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Restore the previously selected profile by key.
        val savedKey = prefs.getString(LAST_SELECTED_PROFILE, null)
        val selectedProfileDef = gattProfiles.find { it.key == savedKey }
        if (selectedProfileDef != null) {
            val index = gattProfiles.indexOf(selectedProfileDef)
            spinner.setSelection(index)
            loadProfileFragment(selectedProfileDef)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: View?, position: Int, id: Long
            ) {
                val selectedProfileDef = gattProfiles[position]
                prefs.edit().putString(LAST_SELECTED_PROFILE, selectedProfileDef.key).apply()
                loadProfileFragment(selectedProfileDef)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        val switchId = menu.findItem(R.id.switchId)
        switchAB = switchId.actionView?.findViewById(R.id.switchAB) as SwitchCompat
        switchAB.isChecked = prefs.getBoolean(GATT_SERVER_ACTIVATED, true)
        switchAB.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().apply {
                putBoolean(GATT_SERVER_ACTIVATED, isChecked)
                if (isChecked) {
                    ApplicationState.getInstance(mContext).ensureYouGattMeServerIsRunning()
                } else {
                    ApplicationState.getInstance(mContext).stopYouGattMeServer()
                }
                apply()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDetach() {
        super.onDetach()
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // Loads the fragment using the fragmentFactory of the selected profile.
    private fun loadProfileFragment(profileDef: GattProfileDef) {
        val fragment = profileDef.fragmentFactory()
        childFragmentManager.beginTransaction()
            .replace(R.id.profileFragmentContainer, fragment)
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                findNavController().navigate(R.id.open_settings_fragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
