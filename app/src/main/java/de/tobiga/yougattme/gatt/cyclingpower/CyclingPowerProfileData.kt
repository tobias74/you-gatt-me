package de.tobiga.yougattme.gatt.cyclingpower

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class CyclingPowerProfileData {

    private val _instantaneousPower = MutableLiveData<Int>(240)

    var instantaneousPower: Int
        get() = _instantaneousPower.value ?: 0
        set(value) { _instantaneousPower.value = value }

    val instantaneousPowerLiveData: LiveData<Int>
        get() = _instantaneousPower

    fun postInstantaneousPower(value: Int) {
        _instantaneousPower.postValue(value)
    }
}
