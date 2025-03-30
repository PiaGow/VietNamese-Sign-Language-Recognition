# VietNamese-Sign-Language-Recognition
🤟 Real-time Sign Language Recognition App:
A complete mobile + AI system that detects and translates Vietnamese sign language gestures in real time.
The project combines Android app development, computer vision, and deep learning to help bridge communication gaps for sign language users.

📌 Description:
This project allows users to perform Vietnamese sign language in front of their phone’s camera.
The system records video, detects body and hand movements, and uses a trained AI model to predict the meaning of the gesture.
It works in real time, providing fast and reliable feedback to the user directly on the mobile screen.

🚀 Key Features:
Real-time gesture capture using Android's CameraX with live preview, camera flip, and flash control.

AI-powered recognition with a custom-trained LSTM model using data from MediaPipe Holistic (pose + hands).

Confidence filtering: Only displays results when the model is ≥ 80% confident and consistent across multiple frames.

Client-server communication: Captured frames are compiled into .mp4 video and sent to the server using HTTP Multipart.

Fast backend response using FastAPI, returning predictions in JSON format for immediate display.

Supports recognition of 13 common Vietnamese signs, such as "Bố mẹ", "Con dê", "Ngày của mẹ", etc.

📊 Data Collection:
Sign language samples were recorded as short videos for each gesture class.

MediaPipe Holistic was used to extract 3D landmarks for:

Pose (33 points)

Left hand (21 points)

Right hand (21 points)

Each gesture is represented as a sequence of 60 frames × 99 features.

Data saved as .npy format for efficient loading and training.

Applied data augmentation to increase variation and improve model robustness (e.g. flipping, jittering).

🧠 Model Training:
Model type: LSTM (Long Short-Term Memory) sequence model using TensorFlow/Keras.

Input: 60 x 99 sequences (60 frames per gesture, 99 keypoints per frame).

Output: Softmax classification into 13 gesture classes.

Training:

Performed using preprocessed .npy landmark data.

Monitored validation accuracy and loss to select the best model.

Final model saved as: best_lstm_model_v5.keras.

🔄 Real-time Prediction Workflow:
On Android App:
CameraX captures live frames (JPEG).

BitmapUtils converts raw camera data (YUV) to JPEG.

VideoUtils combines 70 frames into an .mp4 video file.

The app sends the video to the backend using Retrofit (HTTP Multipart).

Displays the prediction result (gesture name) on the app screen.

On Backend (FastAPI):
Receives the uploaded video at /api/v1/upload.

Extracts landmarks using MediaPipe Holistic.

Feeds the landmark sequence into the LSTM model.

Returns the predicted gesture if confidence ≥ 0.8.

Sends result as JSON response:
Example: { "predictions": "Hoc tap" }

⚙️ Prerequisites:
✅ Android App
Android Studio

Java 8+

CameraX

Retrofit

Minimum SDK: 21

Internet permission

✅ Python Backend
Python 3.8+

FastAPI

TensorFlow / Keras

MediaPipe

OpenCV

Uvicorn

NumPy

✅ Conclusion:
This project is a practical demonstration of how machine learning and mobile development can be combined to create inclusive, real-world applications.
It can be extended with:

More gesture classes

Multi-user support

Real-time translation to text or speech

