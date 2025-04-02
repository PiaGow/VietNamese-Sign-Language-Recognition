from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
import cv2
import numpy as np
import mediapipe as mp
from tensorflow.keras.models import load_model
import io
import uvicorn

app = FastAPI()

# Hàm dự đoán và xử lý video
def predict_and_display_on_video(video_file, model, holistic_model, timesteps=70):
    # Mở video từ file bytes bằng cách sử dụng OpenCV
    video_bytes = io.BytesIO(video_file)
    
    # Đọc video từ byte và chuyển thành các khung hình
    video = cv2.VideoCapture()
    video.open(video_bytes)
    if video is None:
        raise ValueError("Video không thể được giải mã từ dữ liệu byte.")
    # Các biến dùng cho quá trình dự đoán
    frame_width = video.shape[1]  # Chiều rộng của video
    frame_height = video.shape[0]  # Chiều cao của video
    frames = []  # Lưu trữ landmarks của 70 frame gần nhất
    output_list = []  # Lưu trữ các output theo yêu cầu
    skip_frames = False  # Cờ để kiểm soát việc thêm frame vào frames

    action_labels = ['Ai cho', 'Bo me', 'Bun oc', 'Co giao', 'Con de',
                     'Day', 'Hoc tap', 'Hoc tro', 'Luoi', 'Ngay cua me',
                     'Qua', 'Mon tin hoc va van phong', 'Tu giac']
    
    results = []

    while True:
        ret, frame = video.read()
        if not ret:
            break

        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results_holistic = holistic_model.process(frame_rgb)

        pose_landmarks = np.array([
            [lm.x, lm.y, lm.z] for lm in results_holistic.pose_landmarks.landmark
        ]) if results_holistic.pose_landmarks else np.zeros((33, 3))  # Pose landmarks

        left_hand_landmarks = [
            [lm.x, lm.y, lm.z]
            for lm in results_holistic.left_hand_landmarks.landmark
        ] if results_holistic.left_hand_landmarks else [[0, 0, 0]] * 21

        right_hand_landmarks = [
            [lm.x, lm.y, lm.z]
            for lm in results_holistic.right_hand_landmarks.landmark
        ] if results_holistic.right_hand_landmarks else [[0, 0, 0]] * 21

        # Combine tất cả landmarks (Pose + Left hand + Right hand)
        combined_landmarks = np.concatenate((pose_landmarks, left_hand_landmarks, right_hand_landmarks), axis=0)

        if not skip_frames:
            frames.append(combined_landmarks)
            if len(frames) > timesteps:
                frames.pop(0)

        if len(frames) == timesteps:
            frame_data = np.array(frames).reshape(1, timesteps, -1)
            predictions = model.predict(frame_data)
            conf = np.max(predictions)
            predicted_class = np.argmax(predictions, axis=-1)
            action = action_labels[predicted_class[0]]

            if conf > 0.9:
                results.append({'action': action, 'confidence': conf})

            if len(results) >= 10 and all(o['action'] == action for o in results):
                results.clear()
                frames.clear()

    return results

# Tải model và holistics
mp_holistic = mp.solutions.holistic
holistic_model = mp_holistic.Holistic()
model = load_model('best_lstm_model_v5.keras')

@app.post("/api/v1/process_pose")
async def process_pose(file: UploadFile = File(...)):
    # Đọc video từ file upload
    # Lưu video vào thư mục
    with open(f"videos/{file.filename}", "wb") as buffer:
        buffer.write(await file.read())
    try:
        predictions = predict_and_display_on_video(video_file, model, holistic_model)
        return JSONResponse(content={"predictions": predictions})
    except Exception as e:
        print(e)
        return JSONResponse(content={"error": str(e)}, status_code=500)

# Chạy server FastAPI từ Python
if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=9898)
