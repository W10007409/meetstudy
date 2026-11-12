# On-device models

## mobile_face_net.tflite

- **What:** MobileFaceNet face embedding, float32 TFLite export by Qualcomm AI Hub (`qai-hub-models` v0.66.0).
- **Download URL:** https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/mobile_facenet/releases/v0.66.0/mobile_facenet-tflite-float.zip
  (zip contains `mobile_facenet.tflite`, renamed here to `mobile_face_net.tflite`, and `metadata.json`).
- **Model card:** https://huggingface.co/qualcomm/MobileFaceNet
- **License:**
  - Model (as published by Qualcomm): Apache-2.0 (model card "License: apache-2.0").
  - Original weights `mobilefacenet.pt` from https://github.com/foamliu/MobileFaceNet: Apache-2.0,
    https://github.com/foamliu/MobileFaceNet/blob/master/LICENSE
  - Export wrapper code (`qai_hub_models/models/mobile_facenet/model.py`): BSD-3-Clause.
- **SHA-256:** `2046ab776fba2ec7e420767ffe7eb74a7402a8412d3d7ea8b9f4ca8420e5f3d7`
- **Inputs:** two image tensors `img1`, `img2`, float32, RGB, values in **[0, 1]**, shape per metadata `[1, 3, 112, 112]`
  (the app reads the real shapes from the interpreter at load and supports NCHW or NHWC; see logcat tag `MeetStudyFace`).
- **Normalization:** done inside the model (torchvision mean/std), plus test-time flip averaging. Feed raw RGB / 255.
- **Output:** `embeddings` float32 `[2, 128]` — row 0 is the embedding of `img1`, row 1 of `img2`.
  The app feeds the same crop to both inputs and uses row 0 (128-d, not 192-d).
- **Note:** our crop is an upright bounding-box crop, not landmark-aligned as in training; similarity values
  are expected to be lower than LFW numbers. Tune `IDENTITY_THRESHOLD` on device.

## efficientdet_lite0.tflite

- **What:** EfficientDet-Lite0 int8 object detector (COCO, 90 label slots) from MediaPipe model storage; used for "cell phone" detection (`PhoneDetector`).
- **Download URL:** https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite
- **License:** Apache-2.0 (MediaPipe EfficientDet-Lite0 model card; EfficientDet from google/automl, Apache-2.0).
- **SHA-256:** `0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb`
- **Input:** `[1, 320, 320, 3]` UINT8 (raw RGB 0..255).
- **Outputs (read from the interpreter on device):** `[1, 19206, 90]` FLOAT32 class scores (already 0..1) and `[1, 19206, 4]` FLOAT32 box deltas.
  This is the raw head without NMS; the app only checks whether any anchor scores >= 0.5 for "cell phone" and ignores boxes.
- **Class index:** the .tflite is also a zip containing `labels.txt` (90 lines, 10 of them `???`, matching the 90 score channels).
  `cell phone` is line 77, i.e. 0-based index **76** (`person` = 0).
- **Speed:** Galaxy Tab A8 (R8YW70BEYLZ), 4 threads, bitmap resize + inference: avg ~122 ms (limit 150 ms, see `InferenceBudget`).
  With 2 threads and nested-array output it was ~290 ms, so keep the direct ByteBuffer output.
