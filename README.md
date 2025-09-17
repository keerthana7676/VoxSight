# VoxSight: A Command-Based Voice Assistant for the Visually Impaired

## Project Description
VoxSight is an Android-based assistive application designed to help visually impaired individuals live with more freedom and confidence. The app is a voice-activated assistant that operates completely offline, eliminating the need for an internet connection for its core functions.

By using simple voice commands, users can perform a variety of tasks and receive audio feedback in return. The app's primary goal is to address the accessibility gap and provide meaningful technological support to help people with visual impairments lead a safer and more connected life.

## Features
VoxSight is designed to be a lightweight, responsive, and user-friendly tool that combines machine learning and voice interaction to provide independence to its users.

Key features include:

* **Offline Functionality**: The application can be used entirely offline, ensuring reliability in areas with low or no internet connectivity.
* **Voice-Activated Calling and Messaging**: Users can make phone calls or send and receive text messages hands-free, simply by speaking their commands.
* **Object Detection**: Using the device's camera and an on-device machine learning model, VoxSight can detect and announce nearby objects in real-time. This helps users become more aware of their surroundings and increases their mobility and safety.
* **Real-time Information**: The app can provide real-time audio updates on the current time, date, and battery status with a simple voice command.
* **User-Centric Design**: The user interface is minimal and relies primarily on audio feedback, making it compliant with voice interaction and compatible with screen readers for an easy-to-use experience.

## Voice Commands
Here are the commands the VoxSight assistant recognizes:

* **Battery Level**: Say "battery percentage" or "battery level."

* **Time and Date**: Say "time" for the current time or "date" for the current date.

* **Messages**: Say "read messages" or "read my messages" to listen to your messages. Say "send message" or "text" to send a new message.

* **Phone Calls**: Say "call" followed by the contact's name or a phone number.

* **Camera**: Say "open camera" or "camera on" to launch the camera for object detection.

* **Exit App**: To stop the app, say "stop," "stop listening," "exit," or "exit app."

* **Help**: To hear the list of commands again, say "help" or "list commands."

## Technology Stack
The VoxSight application is built on the following technologies to ensure it is technically viable, efficient, and reliable:

* **Development Environment**: Android Studio
* **Languages**: Java and Kotlin
* **Machine Learning**: TensorFlow Lite for on-device machine learning models, including YOLOv10-N for real-time object detection.
* **APIs**: Text-to-Speech (TTS) and Speech Recognition APIs for voice interaction.

## Feasibility
* **Technical**: The app uses stable and well-documented technologies like Android Studio and TensorFlow Lite, which work on most Android devices without needing extra hardware.
* **Operational**: Its voice-first design makes it intuitive and easy to use with minimal learning, and its offline capability ensures it functions reliably in rural or remote areas.
* **Economic**: The project uses open-source tools and is designed for mid-range Android phones, making it a cost-effective solution for both development and end users.

## Future Enhancements
The app's modular design allows for future improvements to enhance its functionality and inclusivity. Potential future enhancements include:

* **Multilingual Voice Support**: Adding support for regional languages to reach a wider audience in a multilingual country like India.
* **Optical Character Recognition (OCR)**: Implementing an AI-based OCR feature to allow the app to read text from books, signs, and labels.
* **Real-time Navigation**: Integrating GPS and voice-guided instructions to help users navigate unfamiliar environments with greater confidence.
* **Distress Alert**: A voice-activated distress feature that could alert emergency services or close contacts with the user's real-time location.

## Contribution
We welcome contributions to the VoxSight project. For any suggestions or bug reports, please feel free to open an issue.
