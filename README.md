# GATT Service Simulator for Android

## 📌 Overview
This project simulates **Bluetooth GATT services** on an **Android device**, enabling developers to test **fitness machine profiles** (FTMS), **cycling speed & cadence (CSC)**, **running speed & cadence (RSC)**, and **cycling power (CP)** without needing actual fitness equipment.

### ✅ Features
- **Simulated GATT services** for multiple fitness profiles
- **Live data updates** via UI controls
- **Configurable BLE characteristics & descriptors**

## 📱 Supported Fitness Machines & Sensors
- **Treadmill** (Speed, Pace, Steps, Heart Rate)
- **Indoor Bike** (Speed, Cadence, Power, Heart Rate)
- **Cross Trainer** (Speed, Stride Count, Power, Heart Rate)
- **Rower** (Stroke Rate, Power, Heart Rate, Pace)
- **Cycling Speed & Cadence (CSC)**
- **Running Speed & Cadence (RSC)**
- **Cycling Power (CP)**

---

## 🔧 Installation & Setup
### Prerequisites
- Bluetooth Low Energy (BLE) support on device

### Build & Run
1. Clone the repository:
   ```sh
   git clone https://github.com/tobias74/you-gatt-me.git
   cd you-gatt-me
   ```
2. Open the project in **Android Studio**.
3. Connect an **Android device** (BLE required) or use an emulator with BLE support.
4. Run the app using **Run → Run 'app'**.

---

## 🎛 Usage
### **1️⃣ Select a Fitness Machine or Sensor Mode**
Choose from **Treadmill, Indoor Bike, Cross Trainer, Rower, Cycling Speed & Cadence, Running Speed & Cadence, or Cycling Power** in the app UI.

### **2️⃣ Configure Data Fields**
Adjust values for:
- Speed, Cadence, Power, Stroke Rate, Steps
- Heart Rate (optional, toggled via checkbox)
- Distance, Elapsed Time (automatically calculated)

### **3️⃣ Start the Simulation**
- The app will **broadcast GATT services**.
- Watch **real-time updates** as the data changes.

---

## 🔬 Project Structure
```
📂 app/
 ├── 📂 src/main/java/de/tobiga/yougattme
 │     ├── 📂 gatt/
 │     │     ├── 📂 fitnessmachine/  # GATT services & characteristics for FTMS
 │     ├── 📂 ui/                    # Android UI fragments
 ├── 📂 res/layout/                  # XML layouts for UI
 ├── 📄 AndroidManifest.xml           # App permissions & BLE setup
```

---

## ⚡️ BLE GATT Implementation
### **1️⃣ GATT Server Setup**
- Registers **FTMS, CSC, RSC, and Cycling Power characteristics**.

### **3️⃣ Custom Data Simulation**
- Data values are updated via **ViewModels** (`TreadmillData`, `IndoorBikeData`, etc.).
- `advanceTime()` recalculates **speed, cadence, power, distance** dynamically.

---

## 🚀 Future Improvements
- 📡 **Support for additional BLE services** (HRM, other cycling metrics)

---

## 🤝 Contributing
1. Fork the repository
2. Create a new branch (`git checkout -b feature-branch`)
3. Commit changes (`git commit -m 'Added new feature'`)
4. Push to the branch (`git push origin feature-branch`)
5. Open a **Pull Request**

---

## 📜 License
MIT License. See `LICENSE` for details.

---

## 📩 Contact
👨‍💻 **Tobias Gassmann**
📧 [mail@tobiga.com](mailto:mail@tobiga.com)
📞 +49 160 96 24 83 98
🐙 GitHub: [tobias74](https://github.com/tobias74)
