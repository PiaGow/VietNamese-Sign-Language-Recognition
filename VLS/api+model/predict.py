import cv2
import numpy as np
import mediapipe as mp
from tensorflow.keras.models import load_model

# Hàm để trích xuất landmarks từ video, dự đoán hành động và hiển thị kết quả lên video
def predict_and_display_on_video(video_path, model, holistic_model, timesteps=30):
    # Mở video
    video = cv2.VideoCapture(video_path)

    frame_width = int(video.get(cv2.CAP_PROP_FRAME_WIDTH))
    frame_height = int(video.get(cv2.CAP_PROP_FRAME_HEIGHT))

    frames = []  # Lưu trữ landmarks của 90 frame gần nhất
    output_list = []  # Lưu trữ các output theo yêu cầu
    skip_frames = False  # Cờ để kiểm soát việc thêm frame vào frames

    while True:
        ret, frame = video.read()
        if not ret:
            break

        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = holistic_model.process(frame_rgb)

        pose_landmarks = np.array([
            [lm.x, lm.y, lm.z] for lm in results.pose_landmarks.landmark
        ]) if results.pose_landmarks else np.zeros((33, 3))  # Pose landmarks

        left_hand_landmarks = [
            [lm.x, lm.y, lm.z]
            for lm in results.left_hand_landmarks.landmark
        ] if results.left_hand_landmarks else [[0, 0, 0]] * 21

        right_hand_landmarks = [
            [lm.x, lm.y, lm.z]
            for lm in results.right_hand_landmarks.landmark
        ] if results.right_hand_landmarks else [[0, 0, 0]] * 21

        # Combine tất cả landmarks (Pose + Left hand + Right hand)
        combined_landmarks = np.concatenate((pose_landmarks, left_hand_landmarks, right_hand_landmarks), axis=0)

        # Thêm landmarks vào frames nếu không skip
        if not skip_frames:
            frames.append(combined_landmarks)
            if len(frames) > timesteps:
                frames.pop(0)

        # Dự đoán khi đủ 90 frame
        if len(frames) == timesteps:
            frame_data = np.array(frames).reshape(1, timesteps, -1)

            predictions = model.predict(frame_data)
            action_labels = ['Ai cho', 'Bo me', 'Bun oc', 'Co giao', 'Con de',
                             'Day', 'Hoc tap', 'Hoc tro', 'Luoi', 'Ngay cua me',
                             'Qua', 'Mon tin hoc va van phong', 'Tu giac']
            conf = np.max(predictions)
            predicted_class = np.argmax(predictions, axis=-1)
            action = action_labels[predicted_class[0]]
            print(action)
            # Kiểm tra confidence và xử lý output list
            if conf > 0.9:
                if len(output_list) == 0 or (output_list[-1]['action'] == action and conf > 0.9):
                    output_list.append({'action': action, 'conf': conf})
                else:
                    output_list = [{'action': action, 'conf': conf}]

            if len(output_list) >= 5 and all(o['action'] == action for o in output_list):
                # output_list.clear()  # Xóa toàn bộ output list
                # skip_frames = True
                # frames.clear()
            # if output_list:
                cv2.putText(frame, f"Predicted: {output_list[-1]['action']} (Conf: {output_list[-1]['conf']:.2f})",
                            (50, 50), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
            if len(output_list) >= 10 and all(o['action'] == action for o in output_list):
                output_list.clear()  # Xóa toàn bộ output list
                frames.clear()
        # Hiển thị video
        cv2.imshow("Video Prediction", frame)

        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

    video.release()
    cv2.destroyAllWindows()

mp_holistic = mp.solutions.holistic
holistic_model = mp_holistic.Holistic()

model = load_model('best_lstm_model_v5.keras')
# model = load_model('best_lstm_model.keras')

# Đường dẫn video
video_path = 0

# Gọi hàm để dự đoán và hiển thị kết quả lên video
predict_and_display_on_video(video_path, model, holistic_model)
