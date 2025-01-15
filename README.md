# Polar PPI Recording Android Application

## Overview
The **Polar PPI Recording Android** application utilizes the **Polar SDK** to connect with **Polar Verity Sense** devices over Bluetooth. It allows users to manage offline **PPI (Pulse-to-Pulse Interval)** recordings seamlessly.

![polarppirecording](https://github.com/user-attachments/assets/cb0cfec2-d1b5-4f8b-baca-0fa9c8aa465a)

## Features

### Device Connection
- Connect to a **Polar Verity Sense** device by entering the device ID or selecting a previously connected ID from a dropdown list.
- Upon successful connection, the device's battery capacity is displayed via a toast message.

### Device Information
- **Get Time**: Fetches the current date and time from the device and displays it as a toast message.
- **Get Disk Space**: Retrieves the device's available disk space and shows it as a toast message.

### PPI Recording Management
- **Recording Status**: Displays the current status of the PPI recording.
- **Start Verity Sense PPI Recording**: Starts offline PPI recording if it hasn't been initiated.
- **Stop Verity Sense PPI Recording**: Stops the ongoing offline PPI recording.
- **List Verity Sense Recordings**: Lists all available recording files on the device and enables the download and delete buttons.

### File Management
- **Download Last Verity Sense Recording**: Downloads the latest recording file from the Polar device and prompts the user to view or open the file.
- **Delete Last Verity Sense Recording**: Deletes the latest recording file from the Polar device, allowing access to previous recordings.

## Recording File Format
The downloaded recording is in **CSV** format with the following structure:

### Headers
- `Polar Device ID`
- `PPI Recording Start Date and Time`

### Data Columns
- `hr`: Heart Rate
- `PPI`: Pulse-to-Pulse Interval
- `blockerBit`: Data integrity indicator
- `skinContactStatus`: Sensor skin contact status
- `skinContactSupported`: Sensor skin contact support flag
- `errorEstimate`: Error estimation value

## Installation
1. Install the app on your **Android** device.
2. Enable **Bluetooth** on your device.
3. Launch the app and connect to a **Polar Verity Sense** device by entering the device ID or selecting from the dropdown list.

## Version
**1.2**

---

## License
This project is licensed under the [MIT License ??]().

## Acknowledgments
- **Polar SDK** for device integration.

For more details, please refer to the official [Polar SDK documentation](https://github.com/polarofficial/polar-ble-sdk).
