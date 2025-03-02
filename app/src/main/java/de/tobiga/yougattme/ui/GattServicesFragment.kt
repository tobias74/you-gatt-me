package de.tobiga.yougattme.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import de.tobiga.yougattme.ApplicationState
import de.tobiga.yougattme.R
import de.tobiga.yougattme.databinding.FragmentGattServicesBinding

class GattServicesFragment : Fragment() {

    private var _binding: FragmentGattServicesBinding? = null
    private val binding get() = _binding!!
    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var switchAB: SwitchCompat
    private lateinit var applicationState: ApplicationState

    companion object {
        const val GATT_SERVER_ACTIVATED = "gatt_server_activated"
    }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Handle preference changes if needed
    }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applicationState = ApplicationState.getInstance(requireContext())

        setupClickListeners()
        setupSwitches()
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

    private fun setupClickListeners() {
        binding.cardFtms.setOnClickListener {
            findNavController().navigate(R.id.action_gattServicesFragment_to_fitnessMachineFragment)
        }

        // New: Navigate to CyclingSpeedAndCadenceFragment
        binding.cardCsc.setOnClickListener {
            findNavController().navigate(R.id.action_gattServicesFragment_to_cyclingSpeedAndCadenceFragment)
        }

        binding.cardPower.setOnClickListener {
            findNavController().navigate(R.id.action_gattServicesFragment_to_cyclingPowerFragment)
        }

        binding.cardRsc.setOnClickListener {
            findNavController().navigate(R.id.action_gattServicesFragment_to_runningSpeedAndCadenceFragment)
        }

    }

    private fun setupSwitches() {
        binding.switchFtms.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                applicationState.activateFitnessMachine()
            } else {
                applicationState.deactivateFitnessMachine()
            }
        }

        binding.switchCsc.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                applicationState.activateCyclingSpeedAndCadence()
            } else {
                applicationState.deactivateCyclingSpeedAndCadence()
            }
        }

        binding.switchPower.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                applicationState.activateCyclingPower()
            } else {
                applicationState.deactivateCyclingPower()
            }
        }

        // New: Running Speed and Cadence switch
        binding.switchRsc.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                applicationState.activateRunningSpeedAndCadence()
            } else {
                applicationState.deactivateRunningSpeedAndCadence()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
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